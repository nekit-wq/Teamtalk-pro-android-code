/*
 * Copyright (c) 2005-2018, BearWare.dk
 *
 * Contact Information:
 *
 * Bjoern D. Rasmussen
 * Kirketoften 5
 * DK-8260 Viby J
 * Denmark
 * Email: contact@bearware.dk
 * Phone: +45 20 20 54 59
 * Web: http://www.bearware.dk
 *
 * This source code is part of the TeamTalk SDK owned by
 * BearWare.dk. Use of this file, or its compiled unit, requires a
 * TeamTalk SDK License Key issued by BearWare.dk.
 *
 * The TeamTalk SDK License Agreement along with its Terms and
 * Conditions are outlined in the file License.txt included with the
 * TeamTalk SDK distribution.
 *
 */
package org.nekit.ttproplus.gui;
import org.nekit.ttproplus.R;

import android.content.Context;
import android.content.Intent;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import dk.bearware.ClientEvent;
import dk.bearware.Channel;
import dk.bearware.User;
import dk.bearware.ClientFlag;
import dk.bearware.ClientStatistics;
import dk.bearware.TeamTalkBase;
import dk.bearware.UserAccount;
import dk.bearware.UserRight;
import dk.bearware.UserState;
import org.nekit.ttproplus.backend.TeamTalkConnection;
import org.nekit.ttproplus.backend.TeamTalkConnectionListener;
import org.nekit.ttproplus.backend.TeamTalkService;
import dk.bearware.events.ClientEventListener;

public class OnlineUsersActivity extends AppCompatActivity implements
        ClientEventListener.OnCmdUserLoggedInListener,
        ClientEventListener.OnCmdUserLoggedOutListener,
        ClientEventListener.OnCmdUserJoinedChannelListener,
        ClientEventListener.OnCmdUserLeftChannelListener,
        ClientEventListener.OnCmdUserUpdateListener, TeamTalkConnectionListener {

    private static final String TAG = "OnlineUsersActivity";

    private TeamTalkConnection mConnection;
    private ListView onlineUsersList;
    private OnlineUserAdapter adapter;
    private final ArrayList<User> onlineUsers = new ArrayList<>();

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
        setContentView(R.layout.activity_online_users);
        EdgeToEdgeHelper.enableEdgeToEdge(this);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        onlineUsersList = findViewById(R.id.online_users_list);
        adapter = new OnlineUserAdapter(this, onlineUsers);
        onlineUsersList.setAdapter(adapter);

        onlineUsersList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                User selectedUser = onlineUsers.get(position);
                Intent intent = new Intent(OnlineUsersActivity.this, UserPropActivity.class);
                intent.putExtra(UserPropActivity.EXTRA_USERID, selectedUser.nUserID);
                startActivity(intent);
            }
        });

        onlineUsersList.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View v, int position, long id) {
                User selectedUser = onlineUsers.get(position);
                AlertDialog.Builder alert = new AlertDialog.Builder(OnlineUsersActivity.this);
                UserAccount myuseraccount = new UserAccount();
                getClient().getMyUserAccount(myuseraccount);
                boolean banRight = (myuseraccount.uUserRights & UserRight.USERRIGHT_BAN_USERS) != UserRight.USERRIGHT_NONE;
                boolean kickRight = (myuseraccount.uUserRights & UserRight.USERRIGHT_KICK_USERS) != UserRight.USERRIGHT_NONE;
                // operator of a channel can also kick users
                int myuserid = getClient().getMyUserID();
                boolean operatorRight = getClient().isChannelOperator(myuserid, selectedUser.nChannelID);

                PopupMenu onlineActions = new PopupMenu(OnlineUsersActivity.this, v);
                onlineActions.inflate(R.menu.online_actions);

                onlineActions.setOnMenuItemClickListener(item -> {
                    int itemId = item.getItemId();
                    if (itemId == R.id.action_edituser) {
                        Intent intent = new Intent(OnlineUsersActivity.this, UserPropActivity.class);
                        startActivity(intent.putExtra(UserPropActivity.EXTRA_USERID, selectedUser.nUserID));
                        return true;
                    } else if (itemId == R.id.action_message) {
                        Intent intent = new Intent(OnlineUsersActivity.this, TextMessageActivity.class);
                        startActivity(intent.putExtra(TextMessageActivity.EXTRA_USERID, selectedUser.nUserID));
                        return true;
                    } else if (itemId == R.id.action_banchan) {
                        confirmAction(alert, R.string.ban_confirmation, selectedUser,
                                () -> banAndKick(selectedUser, selectedUser.nChannelID));
                        return true;
                    } else if (itemId == R.id.action_bansrv) {
                        confirmAction(alert, R.string.ban_confirmation, selectedUser,
                                () -> banAndKick(selectedUser, 0));
                        return true;
                    } else if (itemId == R.id.action_kickchan) {
                        confirmAction(alert, R.string.kick_confirmation, selectedUser,
                                () -> getClient().doKickUser(selectedUser.nUserID, selectedUser.nChannelID));
                        return true;
                    } else if (itemId == R.id.action_kicksrv) {
                        confirmAction(alert, R.string.kick_confirmation, selectedUser,
                                () -> getClient().doKickUser(selectedUser.nUserID, 0));
                        return true;
                    } else if (itemId == R.id.action_makeop) {
                        boolean isOp = getClient().isChannelOperator(selectedUser.nUserID, selectedUser.nChannelID);
                        if ((myuseraccount.uUserRights & UserRight.USERRIGHT_OPERATOR_ENABLE) != UserRight.USERRIGHT_NONE) {
                            getClient().doChannelOp(selectedUser.nUserID, selectedUser.nChannelID, !isOp);
                            return true;
                        }
                        alert.setTitle(!isOp ? R.string.action_revoke_operator : R.string.action_make_operator);
                        alert.setMessage(R.string.text_operator_password);
                        final EditText input = new EditText(OnlineUsersActivity.this);
                        input.setInputType(InputType.TYPE_TEXT_VARIATION_PASSWORD | InputType.TYPE_CLASS_TEXT);
                        alert.setPositiveButton(android.R.string.yes, (dialog, whichButton) ->
                                getClient().doChannelOpEx(selectedUser.nUserID, selectedUser.nChannelID, input.getText().toString(), !isOp));
                        alert.setNegativeButton(android.R.string.no, null);
                        alert.setView(input);
                        alert.show();
                        return true;
                    } else {
                        return false;
                    }
                });

            onlineActions.getMenu().findItem(R.id.action_kickchan).setEnabled(kickRight | operatorRight).setVisible(kickRight | operatorRight);
            onlineActions.getMenu().findItem(R.id.action_kicksrv).setEnabled(kickRight).setVisible(kickRight);
            onlineActions.getMenu().findItem(R.id.action_banchan).setEnabled(banRight | operatorRight).setVisible(banRight | operatorRight);
            onlineActions.getMenu().findItem(R.id.action_bansrv).setEnabled(banRight).setVisible(banRight);
            onlineActions.getMenu().findItem(R.id.action_makeop).setTitle(getClient().isChannelOperator(selectedUser.nUserID , selectedUser.nChannelID) ? R.string.action_revoke_operator : R.string.action_make_operator);

                onlineActions.show();
                return true;
            }
        });

        Intent intent = new Intent(this, TeamTalkService.class);
        if(!bindService(intent, mConnection, Context.BIND_AUTO_CREATE)) {
            Log.e(TAG, "Failed to bind to TeamTalk service");
        }
    }

    @Override
    protected void onDestroy() {
        if (mConnection.isBound()) {
            getService().getEventHandler().unregisterListener(this);
            unbindService(mConnection);
        }
        super.onDestroy();
    }

    private void confirmAction(AlertDialog.Builder alert, int messageResId, User user, Runnable action) {
        alert.setMessage(getString(messageResId, user.szNickname));
        alert.setPositiveButton(android.R.string.yes, (dialog, which) -> action.run());
        alert.setNegativeButton(android.R.string.no, null);
        alert.show();
    }

    private void banAndKick(User user, int channelId) {
        getClient().doBanUser(user.nUserID, channelId);
        getClient().doKickUser(user.nUserID, channelId);
    }

    private void registerEventListeners() {
        getService().getEventHandler().registerOnCmdUserLoggedIn(this, true);
        getService().getEventHandler().registerOnCmdUserLoggedOut(this, true);
        getService().getEventHandler().registerOnCmdUserJoinedChannel(this, true);
        getService().getEventHandler().registerOnCmdUserLeftChannel(this, true);
        getService().getEventHandler().registerOnCmdUserUpdate(this, true);
    }

    private void populateUserList() {
        onlineUsers.clear();
        onlineUsers.addAll(getService().getUsers().values());
        sortAndNotifyDataSetChanged();
    }

    private void sortAndNotifyDataSetChanged() {
        onlineUsers.sort(Comparator.comparing(u -> u.szNickname.toLowerCase(Locale.ROOT)));
        adapter.notifyDataSetChanged();
    }

    private int findUserIndex(User user) {
        for (int i = 0; i < onlineUsers.size(); i++) {
            if (onlineUsers.get(i).nUserID == user.nUserID) {
                return i;
            }
        }
        return -1;
    }

    private void updateUser(User user) {
        int index = findUserIndex(user);
        if (index != -1) {
            onlineUsers.set(index, user);
        } else {
            onlineUsers.add(user);
        }
        sortAndNotifyDataSetChanged();
    }

    @Override
    public void onCmdUserLoggedIn(User user) {
        Log.d(TAG, "User logged in: " + user.szNickname);
        updateUser(user);
    }

    @Override
    public void onCmdUserLoggedOut(User user) {
        Log.d(TAG, "User logged out: " + user.szNickname);
        int index = findUserIndex(user);
        if (index != -1) {
            onlineUsers.remove(index);
        }
        sortAndNotifyDataSetChanged();
    }

    @Override
    public void onCmdUserJoinedChannel(User user) {
        Log.d(TAG, "User " + user.szNickname + " joined channel " + user.nChannelID);
        updateUser(user);
    }

    @Override
    public void onCmdUserLeftChannel(int nChannelID, User user) {
        Log.d(TAG, "User " + user.szNickname + " left channel " + nChannelID);
        updateUser(user);
    }

    @Override
    public void onCmdUserUpdate(User user) {
        Log.d(TAG, "User updated: " + user.szNickname);
        updateUser(user);
    }

    @Override
    public void onServiceConnected(TeamTalkService service) {
        isAdmin = checkIsAdmin();
        registerEventListeners();
        populateUserList();
    }

    @Override
    public void onServiceDisconnected(TeamTalkService service) {

    }

    private final int[] avatarColors = {
        0xFFE53935, 0xFF1E88E5, 0xFF43A047, 0xFFFB8C00, 0xFF8E24AA,
        0xFF00ACC1, 0xFF3949AB, 0xFFD81B60, 0xFF546E7A, 0xFF6D4C41,
        0xFFC0CA33, 0xFFF4511E, 0xFF00897B, 0xFF5C6BC0, 0xFFEC407A
    };

    private boolean isAdmin;
    private final Map<String, String> locationCache = new HashMap<>();

    private boolean checkIsAdmin() {
        UserAccount myAccount = new UserAccount();
        return getClient().getMyUserAccount(myAccount) &&
               (myAccount.uUserRights & UserRight.USERRIGHT_VIEW_ALL_USERS) != 0;
    }

    private class OnlineUserAdapter extends ArrayAdapter<User> {
        private final LayoutInflater inflater;

        public OnlineUserAdapter(Context context, List<User> users) {
            super(context, R.layout.item_online_user, users);
            inflater = LayoutInflater.from(context);
        }

        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
            if (convertView == null) {
                convertView = inflater.inflate(R.layout.item_online_user, parent, false);
            }

            TextView avatarView = convertView.findViewById(R.id.avatar);
            TextView nicknameView = convertView.findViewById(R.id.nickname);
            TextView statusView = convertView.findViewById(R.id.status_msg);
            TextView channelView = convertView.findViewById(R.id.channel_info);
            User user = getItem(position);

            if (user != null) {
                String nickname = user.szNickname;
                nicknameView.setText(nickname);

                String firstLetter = nickname.isEmpty() ? "?" : nickname.substring(0, 1).toUpperCase(Locale.ROOT);
                avatarView.setText(firstLetter);

                int colorIndex = Math.abs(user.nUserID) % avatarColors.length;
                GradientDrawable drawable = (GradientDrawable) avatarView.getBackground();
                drawable.setColor(avatarColors[colorIndex]);

                if (!user.szStatusMsg.isEmpty()) {
                    statusView.setText(user.szStatusMsg);
                    statusView.setVisibility(View.VISIBLE);
                } else {
                    statusView.setVisibility(View.GONE);
                }

                StringBuilder channelInfo = new StringBuilder();
                if (getService().getChannels() != null) {
                    Channel userChannel = getService().getChannels().get(user.nChannelID);
                    if (userChannel != null) {
                        channelInfo.append(userChannel.szName);
                    }
                }

                if (!user.szIPAddress.isEmpty()) {
                    if (channelInfo.length() > 0) channelInfo.append(" · ");
                    channelInfo.append(user.szIPAddress);
                }

                if (isAdmin && !user.szIPAddress.isEmpty()) {
                    String loc = locationCache.get(user.szIPAddress);
                    if (loc != null) {
                        channelInfo.append(" [").append(loc).append("]");
                    } else {
                        IpGeoLocator.getLocation(user.szIPAddress, getContext(), result -> {
                            if (result != null) {
                                locationCache.put(user.szIPAddress, result);
                                notifyDataSetChanged();
                            }
                        });
                    }
                }

                if (!user.szClientName.isEmpty()) {
                    if (channelInfo.length() > 0) channelInfo.append(" · ");
                    channelInfo.append(user.szClientName);
                    String clientVersion = ((user.uVersion >> 16) & 0xFF) + "." + ((user.uVersion >> 8) & 0xFF) + "." + (user.uVersion & 0xFF);
                    channelInfo.append(" ").append(clientVersion);
                }

                channelView.setText(channelInfo.toString());
            }

            return convertView;
        }
    }
}