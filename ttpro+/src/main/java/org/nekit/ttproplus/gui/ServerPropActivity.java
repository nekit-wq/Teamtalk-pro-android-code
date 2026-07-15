package org.nekit.ttproplus.gui;
import org.nekit.ttproplus.R;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.tabs.TabLayout;

import dk.bearware.ServerLogEvent;
import dk.bearware.ServerProperties;
import dk.bearware.TeamTalkBase;
import dk.bearware.UserType;
import org.nekit.ttproplus.backend.TeamTalkConnection;
import org.nekit.ttproplus.backend.TeamTalkConnectionListener;
import org.nekit.ttproplus.backend.TeamTalkService;

public class ServerPropActivity extends AppCompatActivity implements TeamTalkConnectionListener {

    private TeamTalkConnection mConnection;
    private Button btnSave;
    private ServerPropPagerAdapter pagerAdapter;
    public ServerProperties mProps = new ServerProperties();

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
        setContentView(R.layout.activity_server_properties);
        EdgeToEdgeHelper.enableEdgeToEdge(this);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.pref_title_server_info);
        }

        TabLayout tabLayout = findViewById(R.id.server_prop_tabs);
        ViewPager viewPager = findViewById(R.id.server_prop_viewpager);
        btnSave = findViewById(R.id.btn_save_server_props);

        pagerAdapter = new ServerPropPagerAdapter(getSupportFragmentManager());
        viewPager.setAdapter(pagerAdapter);
        viewPager.setOffscreenPageLimit(4);

        tabLayout.setupWithViewPager(viewPager);

        btnSave.setOnClickListener(v -> saveServerProperties());

        mConnection = new TeamTalkConnection(this);
        Intent intent = new Intent(this, TeamTalkService.class);
        if (!bindService(intent, mConnection, Context.BIND_AUTO_CREATE)) {
            Toast.makeText(this, R.string.err_service_not_available, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        if (mConnection.isBound()) {
            unbindService(mConnection);
        }
        super.onDestroy();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void saveServerProperties() {
        for (Fragment f : getSupportFragmentManager().getFragments()) {
            if (f instanceof ServerPropFragment) {
                ((ServerPropFragment) f).updateProperties(mProps);
            }
        }

        int cmdId = getClient().doUpdateServer(mProps);
        if (cmdId > 0) {
            Toast.makeText(this, R.string.text_cmd_processing, Toast.LENGTH_SHORT).show();
            finish();
        } else {
            Toast.makeText(this, R.string.err_update_server_props_failed, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onServiceConnected(TeamTalkService service) {
        if (getClient().getServerProperties(mProps)) {
            dk.bearware.User me = service.getUsers().get(getClient().getMyUserID());
            boolean isAdmin = (me != null && (me.uUserType & UserType.USERTYPE_ADMIN) == UserType.USERTYPE_ADMIN);
            if (!isAdmin) {
                btnSave.setVisibility(View.GONE);
            } else {
                btnSave.setVisibility(View.VISIBLE);
            }
            refreshFragments(!isAdmin);
        }
    }

    private void refreshFragments(boolean readOnly) {
        for (Fragment f : getSupportFragmentManager().getFragments()) {
            if (f instanceof ServerPropFragment) {
                ((ServerPropFragment) f).refreshUI(mProps, readOnly);
            }
        }
    }

    @Override
    public void onServiceDisconnected(TeamTalkService service) {}

    public interface ServerPropFragment {
        void refreshUI(ServerProperties props, boolean readOnly);
        void updateProperties(ServerProperties props);
    }

    private class ServerPropPagerAdapter extends FragmentPagerAdapter {
        public ServerPropPagerAdapter(FragmentManager fm) {
            super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        }

        @Override
        public int getCount() { return 4; }

        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case 0: return getString(R.string.tab_server_properties);
                case 1: return getString(R.string.tab_bandwidth_limits);
                case 2: return getString(R.string.tab_srv_abuse_prevention);
                case 3: return getString(R.string.tab_logging);
                default: return "";
            }
        }

        @NonNull
        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case 0: return new GeneralPropFragment();
                case 1: return new BandwidthPropFragment();
                case 2: return new AbusePropFragment();
                case 3: return new LoggingPropFragment();
                default: return new GeneralPropFragment();
            }
        }
    }

    public static class GeneralPropFragment extends Fragment implements ServerPropFragment {
        EditText editName, editMOTD, editMaxUsers, editMaxLogins, editTcp, editUdp;
        CheckBox chkAutoSave;
        TextView txtServerVersion;

        @Override
        public View onCreateView(@NonNull android.view.LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View v = inflater.inflate(R.layout.fragment_server_prop_general, container, false);
            editName = v.findViewById(R.id.edit_server_name);
            editMOTD = v.findViewById(R.id.edit_server_motd);
            editMaxUsers = v.findViewById(R.id.edit_server_max_users);
            editMaxLogins = v.findViewById(R.id.edit_server_max_logins_ip);
            editTcp = v.findViewById(R.id.edit_server_tcp);
            editUdp = v.findViewById(R.id.edit_server_udp);
            chkAutoSave = v.findViewById(R.id.chk_server_autosave);
            txtServerVersion = v.findViewById(R.id.text_server_version);

            ServerPropActivity activity = (ServerPropActivity) getActivity();
            if (activity != null && activity.mProps != null) refreshUI(activity.mProps, false);
            return v;
        }

        @Override
        public void refreshUI(ServerProperties props, boolean readOnly) {
            if (editName == null) return;
            editName.setText(props.szServerName);
            editMOTD.setText(props.szMOTD);
            editMaxUsers.setText(String.valueOf(props.nMaxUsers));
            editMaxLogins.setText(String.valueOf(props.nMaxLoginsPerIPAddress));
            editTcp.setText(String.valueOf(props.nTcpPort));
            editUdp.setText(String.valueOf(props.nUdpPort));
            chkAutoSave.setChecked(props.bAutoSave);
            if (txtServerVersion != null) {
                txtServerVersion.setText(props.szServerVersion != null ? props.szServerVersion : "\u2014");
            }
            boolean editable = !readOnly;
            editName.setEnabled(editable);
            editMOTD.setEnabled(editable);
            editMaxUsers.setEnabled(editable);
            editMaxLogins.setEnabled(editable);
            editTcp.setEnabled(editable);
            editUdp.setEnabled(editable);
            chkAutoSave.setEnabled(editable);
        }

        @Override
        public void updateProperties(ServerProperties props) {
            if (editName == null) return;
            props.szServerName = editName.getText().toString();
            props.szMOTD = editMOTD.getText().toString();
            try {
                props.nMaxUsers = Integer.parseInt(editMaxUsers.getText().toString());
                props.nMaxLoginsPerIPAddress = Integer.parseInt(editMaxLogins.getText().toString());
                props.nTcpPort = Integer.parseInt(editTcp.getText().toString());
                props.nUdpPort = Integer.parseInt(editUdp.getText().toString());
            } catch (NumberFormatException ignored) {}
            props.bAutoSave = chkAutoSave.isChecked();
        }
    }

    public static class BandwidthPropFragment extends Fragment implements ServerPropFragment {
        EditText editVoice, editVideo, editMedia, editDesktop, editTotal;

        @Override
        public View onCreateView(@NonNull android.view.LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View v = inflater.inflate(R.layout.fragment_server_prop_bandwidth, container, false);
            editVoice = v.findViewById(R.id.edit_max_voice);
            editVideo = v.findViewById(R.id.edit_max_video);
            editMedia = v.findViewById(R.id.edit_max_media);
            editDesktop = v.findViewById(R.id.edit_max_desktop);
            editTotal = v.findViewById(R.id.edit_max_total);
            ServerPropActivity activity = (ServerPropActivity) getActivity();
            if (activity != null && activity.mProps != null) refreshUI(activity.mProps, false);
            return v;
        }

        @Override
        public void refreshUI(ServerProperties props, boolean readOnly) {
            if (editVoice == null) return;
            editVoice.setText(String.valueOf(props.nMaxVoiceTxPerSecond));
            editVideo.setText(String.valueOf(props.nMaxVideoCaptureTxPerSecond));
            editMedia.setText(String.valueOf(props.nMaxMediaFileTxPerSecond));
            editDesktop.setText(String.valueOf(props.nMaxDesktopTxPerSecond));
            editTotal.setText(String.valueOf(props.nMaxTotalTxPerSecond));
            boolean editable = !readOnly;
            editVoice.setEnabled(editable); editVideo.setEnabled(editable);
            editMedia.setEnabled(editable); editDesktop.setEnabled(editable);
            editTotal.setEnabled(editable);
        }

        @Override
        public void updateProperties(ServerProperties props) {
            if (editVoice == null) return;
            try {
                props.nMaxVoiceTxPerSecond = Integer.parseInt(editVoice.getText().toString());
                props.nMaxVideoCaptureTxPerSecond = Integer.parseInt(editVideo.getText().toString());
                props.nMaxMediaFileTxPerSecond = Integer.parseInt(editMedia.getText().toString());
                props.nMaxDesktopTxPerSecond = Integer.parseInt(editDesktop.getText().toString());
                props.nMaxTotalTxPerSecond = Integer.parseInt(editTotal.getText().toString());
            } catch (NumberFormatException ignored) {}
        }
    }

    public static class AbusePropFragment extends Fragment implements ServerPropFragment {
        EditText editAttempts, editDelay, editTimeout;

        @Override
        public View onCreateView(@NonNull android.view.LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View v = inflater.inflate(R.layout.fragment_server_prop_abuse, container, false);
            editAttempts = v.findViewById(R.id.edit_max_login_attempts);
            editDelay = v.findViewById(R.id.edit_login_delay);
            editTimeout = v.findViewById(R.id.edit_user_timeout);
            ServerPropActivity activity = (ServerPropActivity) getActivity();
            if (activity != null && activity.mProps != null) refreshUI(activity.mProps, false);
            return v;
        }

        @Override
        public void refreshUI(ServerProperties props, boolean readOnly) {
            if (editAttempts == null) return;
            editAttempts.setText(String.valueOf(props.nMaxLoginAttempts));
            editDelay.setText(String.valueOf(props.nLoginDelayMSec));
            editTimeout.setText(String.valueOf(props.nUserTimeout));
            boolean editable = !readOnly;
            editAttempts.setEnabled(editable);
            editDelay.setEnabled(editable);
            editTimeout.setEnabled(editable);
        }

        @Override
        public void updateProperties(ServerProperties props) {
            if (editAttempts == null) return;
            try {
                props.nMaxLoginAttempts = Integer.parseInt(editAttempts.getText().toString());
                props.nLoginDelayMSec = Integer.parseInt(editDelay.getText().toString());
                props.nUserTimeout = Integer.parseInt(editTimeout.getText().toString());
            } catch (NumberFormatException ignored) {}
        }
    }

    public static class LoggingPropFragment extends Fragment implements ServerPropFragment {
        CheckBox chkLogin, chkKick, chkChan, chkSrv, chkFile;

        @Override
        public View onCreateView(@NonNull android.view.LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View v = inflater.inflate(R.layout.fragment_server_prop_logging, container, false);
            chkLogin = v.findViewById(R.id.chk_log_user_login);
            chkKick = v.findViewById(R.id.chk_log_user_kick);
            chkChan = v.findViewById(R.id.chk_log_channel_create);
            chkSrv = v.findViewById(R.id.chk_log_server_update);
            chkFile = v.findViewById(R.id.chk_log_file_transfer);
            ServerPropActivity activity = (ServerPropActivity) getActivity();
            if (activity != null && activity.mProps != null) refreshUI(activity.mProps, false);
            return v;
        }

        @Override
        public void refreshUI(ServerProperties props, boolean readOnly) {
            if (chkLogin == null) return;
            int e = props.uServerLogEvents;
            chkLogin.setChecked((e & (ServerLogEvent.SERVERLOGEVENT_USER_CONNECTED | ServerLogEvent.SERVERLOGEVENT_USER_DISCONNECTED)) != 0);
            chkKick.setChecked((e & (ServerLogEvent.SERVERLOGEVENT_USER_KICKED | ServerLogEvent.SERVERLOGEVENT_USER_BANNED)) != 0);
            chkChan.setChecked((e & (ServerLogEvent.SERVERLOGEVENT_CHANNEL_CREATED | ServerLogEvent.SERVERLOGEVENT_CHANNEL_UPDATED)) != 0);
            chkSrv.setChecked((e & ServerLogEvent.SERVERLOGEVENT_SERVER_UPDATED) != 0);
            chkFile.setChecked((e & ServerLogEvent.SERVERLOGEVENT_FILE_UPLOADED) != 0);
            boolean editable = !readOnly;
            chkLogin.setEnabled(editable); chkKick.setEnabled(editable);
            chkChan.setEnabled(editable); chkSrv.setEnabled(editable);
            chkFile.setEnabled(editable);
        }

        @Override
        public void updateProperties(ServerProperties props) {
            if (chkLogin == null) return;
            int e = 0;
            if (chkLogin.isChecked()) e |= (ServerLogEvent.SERVERLOGEVENT_USER_CONNECTED | ServerLogEvent.SERVERLOGEVENT_USER_DISCONNECTED | ServerLogEvent.SERVERLOGEVENT_USER_LOGGEDIN | ServerLogEvent.SERVERLOGEVENT_USER_LOGGEDOUT);
            if (chkKick.isChecked()) e |= (ServerLogEvent.SERVERLOGEVENT_USER_KICKED | ServerLogEvent.SERVERLOGEVENT_USER_BANNED);
            if (chkChan.isChecked()) e |= (ServerLogEvent.SERVERLOGEVENT_CHANNEL_CREATED | ServerLogEvent.SERVERLOGEVENT_CHANNEL_UPDATED | ServerLogEvent.SERVERLOGEVENT_CHANNEL_REMOVED);
            if (chkSrv.isChecked()) e |= ServerLogEvent.SERVERLOGEVENT_SERVER_UPDATED;
            if (chkFile.isChecked()) e |= (ServerLogEvent.SERVERLOGEVENT_FILE_UPLOADED | ServerLogEvent.SERVERLOGEVENT_FILE_DOWNLOADED | ServerLogEvent.SERVERLOGEVENT_FILE_DELETED);
            props.uServerLogEvents = e;
        }
    }
}
