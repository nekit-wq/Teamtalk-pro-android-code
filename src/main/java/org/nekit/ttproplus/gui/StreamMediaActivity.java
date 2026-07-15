package org.nekit.ttproplus.gui;
import org.nekit.ttproplus.R;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import dk.bearware.AudioFormat;
import dk.bearware.ClientEvent;
import dk.bearware.Codec;
import dk.bearware.MediaFileInfo;
import dk.bearware.MediaFilePlayback;
import dk.bearware.MediaFileStatus;
import dk.bearware.TeamTalkBase;
import dk.bearware.VideoCodec;
import dk.bearware.VideoFormat;
import org.nekit.ttproplus.backend.TeamTalkConnection;
import org.nekit.ttproplus.backend.TeamTalkConnectionListener;
import org.nekit.ttproplus.backend.TeamTalkService;
import org.nekit.ttproplus.data.Permissions;

public class StreamMediaActivity
extends AppCompatActivity implements TeamTalkConnectionListener {

    public static final String TAG = "bearware";
    public static final int REQUEST_STREAM_MEDIA = 1;
    private EditText file_path;
    private static final String lastMedia = "last_media_file";
    TeamTalkConnection mConnection;
    private Button btnSelectFile, btnStream, btnPlayPause, btnStop;
    private TextView txtMediaInfo, txtPosition, txtDuration;
    private SeekBar seekBar;
    private boolean isStreaming;
    private int localPlaybackId;
    private MediaFileInfo mMediaFileInfo;
    private MediaFilePlayback mPlayback;
    private boolean seekBarTouching;
    private Handler handler = new Handler();
    private Runnable progressUpdater;

    TeamTalkService getService() {
        return mConnection.getService();
    }

    TeamTalkBase getClient() {
        return getService().getTTInstance();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        mConnection = new TeamTalkConnection(this);
        setContentView(R.layout.activity_stream_media);
        EdgeToEdgeHelper.enableEdgeToEdge(this);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        file_path = findViewById(R.id.file_path_txt);
        file_path.setText(PreferenceManager.getDefaultSharedPreferences(getBaseContext()).getString(lastMedia, ""));

        btnSelectFile = findViewById(R.id.media_file_select_btn);
        btnStream = findViewById(R.id.btn_stream);
        btnPlayPause = findViewById(R.id.btn_play_pause);
        btnStop = findViewById(R.id.btn_stop);
        txtMediaInfo = findViewById(R.id.media_info);
        txtPosition = findViewById(R.id.txt_position);
        txtDuration = findViewById(R.id.txt_duration);
        seekBar = findViewById(R.id.seek_bar);

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && mMediaFileInfo != null && mMediaFileInfo.uDurationMSec > 0) {
                    int pos = (int) ((long) mMediaFileInfo.uDurationMSec * progress / seekBar.getMax());
                    txtPosition.setText(formatDuration(pos));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                seekBarTouching = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                seekBarTouching = false;
                if (mMediaFileInfo == null || mMediaFileInfo.uDurationMSec <= 0) return;
                int offset = (int) ((long) mMediaFileInfo.uDurationMSec * seekBar.getProgress() / seekBar.getMax());
                mPlayback.uOffsetMSec = offset;
                mPlayback.bPaused = false;

                if (localPlaybackId > 0) {
                    getClient().updateLocalPlayback(localPlaybackId, mPlayback);
                } else if (isStreaming) {
                    VideoCodec vc = new VideoCodec();
                    vc.nCodec = Codec.NO_CODEC;
                    getClient().updateStreamingMediaFileToChannel(mPlayback, vc);
                }
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.text_message, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        } else if (id == android.R.id.home) {
            stopLocalPlayback();
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!mConnection.isBound()) {
            Intent intent = new Intent(getApplicationContext(), TeamTalkService.class);
            if (!bindService(intent, mConnection, Context.BIND_AUTO_CREATE))
                Log.e(TAG, "Failed to bind to TeamTalk service");
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mConnection.isBound()) {
            onServiceDisconnected(getService());
            unbindService(mConnection);
            mConnection.setBound(false);
        }
        handler.removeCallbacks(progressUpdater);
    }

    @Override
    protected void onDestroy() {
        stopLocalPlayback();
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Permissions granted = Permissions.onRequestResult(this, requestCode, grantResults);
        if (granted == null)
            return;
        if (granted == Permissions.READ_EXTERNAL_STORAGE ||
            granted == Permissions.READ_MEDIA_VIDEO ||
            granted == Permissions.READ_MEDIA_AUDIO) {
            if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) || areMediaPermissionsComplete())
                mediaSelectionStart();
        }
    }

    @Override
    public void onServiceConnected(TeamTalkService service) {
        getService().getEventHandler().registerOnStreamMediaFile(mfi -> runOnUiThread(() -> onStreamMediaFile(mfi)), true);
        getService().getEventHandler().registerOnLocalMediaFile(mfi -> runOnUiThread(() -> onLocalMediaFile(mfi)), true);

        btnSelectFile.setOnClickListener(v -> {
            if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ?
                requestMediaPermissions() :
                Permissions.READ_EXTERNAL_STORAGE.request(this)) {
                mediaSelectionStart();
            }
        });

        btnStream.setOnClickListener(v -> toggleStreaming());

        btnPlayPause.setOnClickListener(v -> togglePlayPause());

        btnStop.setOnClickListener(v -> stopLocalPlayback());
    }

    @Override
    public void onServiceDisconnected(TeamTalkService service) {
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if ((requestCode == REQUEST_STREAM_MEDIA) && (resultCode == RESULT_OK)) {
            Uri uri = data.getData();
            String path = AbsolutePathHelper.getRealPath(this.getBaseContext(), uri);
            if (path == null && uri != null) {
                path = copyUriToCache(uri);
            }
            if (path != null) {
                file_path.setText(path);
                loadMediaFileInfo(path);
            } else {
                Toast.makeText(this, R.string.err_stream_media, Toast.LENGTH_LONG).show();
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private String copyUriToCache(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            if (is == null) return null;
            File cacheDir = getCacheDir();
            File tempFile = File.createTempFile("media_", ".tmp", cacheDir);
            FileOutputStream os = new FileOutputStream(tempFile);
            byte[] buf = new byte[8192];
            int len;
            while ((len = is.read(buf)) > 0) {
                os.write(buf, 0, len);
            }
            os.close();
            is.close();
            return tempFile.getAbsolutePath();
        } catch (Exception e) {
            Log.e(TAG, "Failed to copy URI to cache", e);
            return null;
        }
    }

    private void mediaSelectionStart() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        Intent i = Intent.createChooser(intent, "File");
        startActivityForResult(i, REQUEST_STREAM_MEDIA);
    }

    private boolean requestMediaPermissions() {
        boolean video = Permissions.READ_MEDIA_VIDEO.request(this);
        boolean audio = Permissions.READ_MEDIA_AUDIO.request(this);
        return areMediaPermissionsComplete() && (video || audio);
    }

    private boolean areMediaPermissionsComplete() {
        return !(Permissions.READ_MEDIA_VIDEO.isPending() ||
                 Permissions.READ_MEDIA_AUDIO.isPending());
    }

    private void loadMediaFileInfo(String path) {
        MediaFileInfo info = new MediaFileInfo();
        if (TeamTalkBase.getMediaFileInfo(path, info)) {
            mMediaFileInfo = info;
            showMediaFileInfo(info);
            updateSeekBar();
        } else {
            mMediaFileInfo = null;
            txtMediaInfo.setVisibility(View.GONE);
            txtDuration.setText("00:00");
        }
    }

    private void showMediaFileInfo(MediaFileInfo info) {
        StringBuilder sb = new StringBuilder();
        sb.append(getString(R.string.label_audio)).append(": ");
        if (info.audioFmt != null && info.audioFmt.nAudioFmt > 0) {
            sb.append(info.audioFmt.nSampleRate).append(" Hz, ");
            sb.append(info.audioFmt.nChannels).append(" ch");
        } else {
            sb.append(getString(R.string.msg_no_audio));
        }
        sb.append("\n").append(getString(R.string.label_video)).append(": ");
        if (info.videoFmt != null && info.videoFmt.picFourCC != 0) {
            sb.append(info.videoFmt.nWidth).append("x").append(info.videoFmt.nHeight);
        } else {
            sb.append(getString(R.string.msg_no_video));
        }
        sb.append("\n").append(getString(R.string.label_duration)).append(": ").append(formatDuration(info.uDurationMSec));
        txtMediaInfo.setText(sb.toString());
        txtMediaInfo.setVisibility(View.VISIBLE);
        txtDuration.setText(formatDuration(info.uDurationMSec));
    }

    private void toggleStreaming() {
        if (isStreaming) {
            getClient().stopStreamingMediaFileToChannel();
            isStreaming = false;
            btnStream.setText(R.string.button_stream_media_file);
            Toast.makeText(this, R.string.msg_stream_stopped, Toast.LENGTH_SHORT).show();
        } else {
            String path = file_path.getText().toString();
            if (path.isEmpty()) return;

            if (mMediaFileInfo == null) {
                loadMediaFileInfo(path);
            }

            VideoCodec videocodec = new VideoCodec();
            videocodec.nCodec = Codec.NO_CODEC;
            if (mMediaFileInfo != null && mMediaFileInfo.videoFmt != null && mMediaFileInfo.videoFmt.picFourCC != 0) {
                videocodec.nCodec = Codec.WEBM_VP8_CODEC;
            }

            if (!getClient().startStreamingMediaFileToChannel(path, videocodec)) {
                Toast.makeText(this, R.string.err_stream_media, Toast.LENGTH_LONG).show();
            } else {
                isStreaming = true;
                btnStream.setText(R.string.action_stop);
                Toast.makeText(this, R.string.msg_stream_started, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void togglePlayPause() {
        String path = file_path.getText().toString();
        if (path.isEmpty()) return;

        if (localPlaybackId > 0) {
            mPlayback.bPaused = !mPlayback.bPaused;
            mPlayback.uOffsetMSec = -1;
            if (getClient().updateLocalPlayback(localPlaybackId, mPlayback)) {
                btnPlayPause.setText(mPlayback.bPaused ? R.string.action_play : R.string.action_pause);
            }
        } else {
            if (mMediaFileInfo == null) {
                loadMediaFileInfo(path);
            }
            mPlayback = new MediaFilePlayback();
            mPlayback.bPaused = false;
            mPlayback.uOffsetMSec = (int) ((long) mMediaFileInfo.uDurationMSec * seekBar.getProgress() / seekBar.getMax());
            localPlaybackId = getClient().initLocalPlayback(path, mPlayback);
            if (localPlaybackId > 0) {
                btnPlayPause.setText(R.string.action_pause);
                btnStop.setEnabled(true);
                startProgressUpdater();
            } else {
                Toast.makeText(this, R.string.err_play_media, Toast.LENGTH_LONG).show();
            }
        }
    }

    private void stopLocalPlayback() {
        if (localPlaybackId > 0) {
            getClient().stopLocalPlayback(localPlaybackId);
            localPlaybackId = 0;
        }
        btnPlayPause.setText(R.string.action_play);
        btnStop.setEnabled(false);
        seekBar.setProgress(0);
        txtPosition.setText("00:00");
        handler.removeCallbacks(progressUpdater);
    }

    private void onStreamMediaFile(MediaFileInfo mfi) {
        mMediaFileInfo = mfi;
        if (mfi.uDurationMSec > 0) {
            txtDuration.setText(formatDuration(mfi.uDurationMSec));
            if (!seekBarTouching)
                updateSeekBarPosition(mfi.uElapsedMSec);
        }
        switch (mfi.nStatus) {
            case MediaFileStatus.MFS_STARTED:
            case MediaFileStatus.MFS_PLAYING:
                btnStream.setText(R.string.action_stop);
                break;
            case MediaFileStatus.MFS_PAUSED:
                break;
            case MediaFileStatus.MFS_ERROR:
            case MediaFileStatus.MFS_FINISHED:
            case MediaFileStatus.MFS_ABORTED:
                isStreaming = false;
                btnStream.setText(R.string.button_stream_media_file);
                break;
        }
    }

    private void onLocalMediaFile(MediaFileInfo mfi) {
        mMediaFileInfo = mfi;
        if (!seekBarTouching && mfi.uDurationMSec > 0)
            updateSeekBarPosition(mfi.uElapsedMSec);

        switch (mfi.nStatus) {
            case MediaFileStatus.MFS_STARTED:
            case MediaFileStatus.MFS_PLAYING:
                btnPlayPause.setText(R.string.action_pause);
                break;
            case MediaFileStatus.MFS_PAUSED:
                btnPlayPause.setText(R.string.action_play);
                break;
            case MediaFileStatus.MFS_ERROR:
            case MediaFileStatus.MFS_FINISHED:
            case MediaFileStatus.MFS_ABORTED:
                localPlaybackId = 0;
                btnPlayPause.setText(R.string.action_play);
                btnStop.setEnabled(false);
                handler.removeCallbacks(progressUpdater);
                if (!seekBarTouching) {
                    seekBar.setProgress(0);
                    txtPosition.setText("00:00");
                }
                break;
        }
    }

    private void startProgressUpdater() {
        handler.removeCallbacks(progressUpdater);
        progressUpdater = new Runnable() {
            @Override
            public void run() {
                if (localPlaybackId > 0 && !seekBarTouching && mMediaFileInfo != null) {
                    updateSeekBarPosition(mMediaFileInfo.uElapsedMSec);
                }
                handler.postDelayed(this, 250);
            }
        };
        handler.postDelayed(progressUpdater, 250);
    }

    private void updateSeekBarPosition(int elapsed) {
        txtPosition.setText(formatDuration(elapsed));
        if (mMediaFileInfo != null && mMediaFileInfo.uDurationMSec > 0) {
            int progress = (int) ((long) elapsed * seekBar.getMax() / mMediaFileInfo.uDurationMSec);
            seekBar.setProgress(progress);
        }
    }

    private void updateSeekBar() {
        if (mMediaFileInfo != null && mMediaFileInfo.uDurationMSec > 0) {
            seekBar.setEnabled(true);
        } else {
            seekBar.setEnabled(false);
        }
    }

    private String formatDuration(int msec) {
        int sec = msec / 1000;
        int min = sec / 60;
        sec = sec % 60;
        return String.format("%02d:%02d", min, sec);
    }
}
