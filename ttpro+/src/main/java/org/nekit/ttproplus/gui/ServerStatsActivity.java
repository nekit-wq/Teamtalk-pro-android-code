package org.nekit.ttproplus.gui;
import org.nekit.ttproplus.R;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import dk.bearware.ServerStatistics;
import dk.bearware.TeamTalkBase;
import org.nekit.ttproplus.backend.TeamTalkConnection;
import org.nekit.ttproplus.backend.TeamTalkConnectionListener;
import org.nekit.ttproplus.backend.TeamTalkService;
import dk.bearware.events.ServerStatsHelper;

public class ServerStatsActivity extends AppCompatActivity implements TeamTalkConnectionListener {

    private TeamTalkConnection mConnection;
    private TextView txtTotal, txtVoice, txtVideo, txtMedia, txtDesktop, txtUptime;
    private CheckBox chkAutoRefresh;
    private Button btnRefresh;
    private Handler handler = new Handler();
    private Runnable refreshRunnable;
    private ServerStatsHelper.OnServerStatisticsListener statsListener;
    private boolean autoRefresh;
    private ServerStatistics lastStats;

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
        setContentView(R.layout.activity_server_stats);
        EdgeToEdgeHelper.enableEdgeToEdge(this);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.title_activity_server_stats);
        }

        txtTotal = findViewById(R.id.txt_total_rxtx);
        txtVoice = findViewById(R.id.txt_voice_rxtx);
        txtVideo = findViewById(R.id.txt_video_rxtx);
        txtMedia = findViewById(R.id.txt_mediafile_rxtx);
        txtDesktop = findViewById(R.id.txt_desktop_rxtx);
        txtUptime = findViewById(R.id.txt_uptime);
        chkAutoRefresh = findViewById(R.id.chk_auto_refresh);
        btnRefresh = findViewById(R.id.btn_refresh);

        statsListener = stats -> runOnUiThread(() -> updateStats(stats));

        chkAutoRefresh.setOnCheckedChangeListener((buttonView, isChecked) -> {
            autoRefresh = isChecked;
            if (isChecked) startAutoRefresh();
            else stopAutoRefresh();
        });

        btnRefresh.setOnClickListener(v -> queryStats());

        mConnection = new TeamTalkConnection(this);
        Intent intent = new Intent(this, TeamTalkService.class);
        if (!bindService(intent, mConnection, Context.BIND_AUTO_CREATE)) {
            Toast.makeText(this, R.string.err_service_not_available, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        stopAutoRefresh();
        if (mConnection.isBound() && getService() != null) {
            ServerStatsHelper.unregisterListener(getService().getEventHandler(), statsListener);
            unbindService(mConnection);
        }
        super.onDestroy();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    public void onServiceConnected(TeamTalkService service) {
        ServerStatsHelper.registerListener(service.getEventHandler(), statsListener);
        queryStats();
    }

    @Override
    public void onServiceDisconnected(TeamTalkService service) {
        stopAutoRefresh();
    }

    private void queryStats() {
        if (getClient() != null) {
            getClient().doQueryServerStats();
        }
    }

    private void updateStats(ServerStatistics stats) {
        lastStats = stats;

        txtTotal.setText(getString(R.string.server_stats_total, formatBytes(stats.nTotalBytesRX), formatBytes(stats.nTotalBytesTX)));
        txtVoice.setText(getString(R.string.server_stats_voice, formatBytes(stats.nVoiceBytesRX), formatBytes(stats.nVoiceBytesTX)));
        txtVideo.setText(getString(R.string.server_stats_video, formatBytes(stats.nVideoCaptureBytesRX), formatBytes(stats.nVideoCaptureBytesTX)));
        txtMedia.setText(getString(R.string.server_stats_mediafile, formatBytes(stats.nMediaFileBytesRX), formatBytes(stats.nMediaFileBytesTX)));
        txtDesktop.setText(getString(R.string.server_stats_desktop, formatBytes(stats.nDesktopBytesRX), formatBytes(stats.nDesktopBytesTX)));

        long uptimeSec = stats.nUptimeMSec / 1000;
        long hours = uptimeSec / 3600;
        long minutes = (uptimeSec % 3600) / 60;
        long secs = uptimeSec % 60;
        txtUptime.setText(getString(R.string.server_stats_uptime, hours, minutes, secs));
    }

    private void startAutoRefresh() {
        stopAutoRefresh();
        refreshRunnable = new Runnable() {
            @Override
            public void run() {
                queryStats();
                if (autoRefresh)
                    handler.postDelayed(this, 3000);
            }
        };
        handler.postDelayed(refreshRunnable, 3000);
    }

    private void stopAutoRefresh() {
        if (refreshRunnable != null) {
            handler.removeCallbacks(refreshRunnable);
            refreshRunnable = null;
        }
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024)
            return bytes + " B";
        else if (bytes < 1024 * 1024)
            return String.format("%.1f KB", bytes / 1024.0);
        else if (bytes < 1024 * 1024 * 1024)
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        else
            return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
}
