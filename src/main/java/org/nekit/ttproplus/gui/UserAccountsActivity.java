package org.nekit.ttproplus.gui;
import org.nekit.ttproplus.R;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import dk.bearware.TeamTalkBase;
import dk.bearware.UserAccount;
import dk.bearware.UserType;
import org.nekit.ttproplus.backend.TeamTalkConnection;
import org.nekit.ttproplus.backend.TeamTalkConnectionListener;
import org.nekit.ttproplus.backend.TeamTalkService;
import dk.bearware.events.ClientEventListener;

public class UserAccountsActivity extends AppCompatActivity implements
        TeamTalkConnectionListener,
        ClientEventListener.OnCmdUserAccountListener {

    private static final String TAG = "UserAccountsActivity";

    private TeamTalkConnection mConnection;
    private ListView accountsListView;
    private UserAccountAdapter adapter;
    private List<UserAccount> allAccounts = new ArrayList<>();
    private List<UserAccount> filteredAccounts = new ArrayList<>();
    private EditText searchEdit;
    private Spinner sortSpinner;
    private boolean isAscending = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_accounts);
        EdgeToEdgeHelper.enableEdgeToEdge(this);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.user_accounts);
        }

        mConnection = new TeamTalkConnection(this);
        accountsListView = findViewById(R.id.user_accounts_listview);
        searchEdit = findViewById(R.id.search_accounts_edit);

        adapter = new UserAccountAdapter(this, filteredAccounts);
        accountsListView.setAdapter(adapter);

        searchEdit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterAccounts(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        sortSpinner = findViewById(R.id.spinner_sort_accounts);
        ArrayAdapter<String> sortAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,
                new String[]{getString(R.string.sort_ascending), getString(R.string.sort_descending)});
        sortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sortSpinner.setAdapter(sortAdapter);

        sortSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                isAscending = (position == 0);
                filterAccounts(searchEdit.getText().toString());
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        Button btnAdd = findViewById(R.id.btn_add_user_account);
        btnAdd.setOnClickListener(v -> {
            Intent intent = new Intent(this, UserAccountEditActivity.class);
            intent.putExtra(UserAccountEditActivity.EXTRA_IS_EDIT, false);
            startActivity(intent);
        });

        accountsListView.setOnItemLongClickListener((parent, view, position, id) -> {
            UserAccount account = filteredAccounts.get(position);
            showAccountOptions(account);
            return true;
        });

        Intent intent = new Intent(this, TeamTalkService.class);
        if (!bindService(intent, mConnection, Context.BIND_AUTO_CREATE)) {
            Log.e(TAG, "Failed to connect to TeamTalk service");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mConnection.isBound() && getService() != null && getClient() != null) {
            allAccounts.clear();
            getClient().doListUserAccounts(0, 100);
        }
    }

    @Override
    protected void onDestroy() {
        if (mConnection.isBound()) {
            if (getService() != null) {
                getService().getEventHandler().unregisterListener(this);
            }
            unbindService(mConnection);
        }
        super.onDestroy();
    }

    private TeamTalkService getService() {
        return mConnection.getService();
    }

    private TeamTalkBase getClient() {
        return getService().getTTInstance();
    }

    @Override
    public void onServiceConnected(TeamTalkService service) {
        service.getEventHandler().registerOnCmdUserAccount(this, true);
        getClient().doListUserAccounts(0, 100);
    }

    @Override
    public void onServiceDisconnected(TeamTalkService service) {}

    @Override
    public void onCmdUserAccount(UserAccount useraccount) {
        runOnUiThread(() -> {
            boolean found = false;
            for (int i = 0; i < allAccounts.size(); i++) {
                if (allAccounts.get(i).szUsername != null &&
                    allAccounts.get(i).szUsername.equals(useraccount.szUsername)) {
                    allAccounts.set(i, useraccount);
                    found = true;
                    break;
                }
            }
            if (!found) {
                allAccounts.add(useraccount);
            }
            filterAccounts(searchEdit.getText().toString());
        });
    }

    private void filterAccounts(String query) {
        filteredAccounts.clear();
        if (query == null || query.isEmpty()) {
            filteredAccounts.addAll(allAccounts);
        } else {
            String q = query.toLowerCase();
            for (UserAccount acc : allAccounts) {
                if ((acc.szUsername != null && acc.szUsername.toLowerCase().contains(q)) ||
                    (acc.szNote != null && acc.szNote.toLowerCase().contains(q))) {
                    filteredAccounts.add(acc);
                }
            }
        }

        Collections.sort(filteredAccounts, (u1, u2) -> {
            String n1 = (u1.szUsername == null) ? "" : u1.szUsername;
            String n2 = (u2.szUsername == null) ? "" : u2.szUsername;
            return isAscending ? n1.compareToIgnoreCase(n2) : n2.compareToIgnoreCase(n1);
        });

        adapter.notifyDataSetChanged();
    }

    private void showAccountOptions(UserAccount account) {
        String[] options = {
            getString(R.string.action_account_properties),
            getString(R.string.action_edit),
            getString(R.string.action_delete),
        };
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.title_options_for, account.szUsername))
                .setItems(options, (dialog, which) -> handleAccountOption(account, which))
                .show();
    }

    private void handleAccountOption(UserAccount account, int which) {
        if (which == 0 || which == 1) {
            Intent intent = new Intent(this, UserAccountEditActivity.class);
            intent.putExtra(UserAccountEditActivity.EXTRA_IS_VIEW, which == 0);
            intent.putExtra(UserAccountEditActivity.EXTRA_IS_EDIT, which == 1);
            intent.putExtra(UserAccountEditActivity.EXTRA_USERNAME, account.szUsername);
            intent.putExtra(UserAccountEditActivity.EXTRA_PASSWORD, account.szPassword);
            intent.putExtra(UserAccountEditActivity.EXTRA_USERTYPE, account.uUserType);
            intent.putExtra(UserAccountEditActivity.EXTRA_USERRIGHTS, account.uUserRights);
            intent.putExtra(UserAccountEditActivity.EXTRA_NOTE, account.szNote);
            intent.putExtra(UserAccountEditActivity.EXTRA_INIT_CHANNEL, account.szInitChannel);
            intent.putExtra(UserAccountEditActivity.EXTRA_OPERATOR_CHANNELS, account.autoOperatorChannels);
            startActivity(intent);
        } else if (which == 2) {
            confirmDelete(account);
        }
    }

    private void confirmDelete(UserAccount account) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_confirm_delete)
                .setMessage(getString(R.string.msg_confirm_delete_account, account.szUsername))
                .setPositiveButton(android.R.string.yes, (dialog, which) -> {
                    getClient().doDeleteUserAccount(account.szUsername);
                    allAccounts.remove(account);
                    filterAccounts(searchEdit.getText().toString());
                })
                .setNegativeButton(android.R.string.no, null)
                .show();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private class UserAccountAdapter extends ArrayAdapter<UserAccount> {
        public UserAccountAdapter(Context context, List<UserAccount> accounts) {
            super(context, android.R.layout.simple_list_item_2, accounts);
        }

        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(android.R.layout.simple_list_item_2, parent, false);
            }
            UserAccount account = getItem(position);
            TextView text1 = convertView.findViewById(android.R.id.text1);
            TextView text2 = convertView.findViewById(android.R.id.text2);

            if (account != null) {
                String displayName = account.szUsername;
                if (displayName == null || displayName.trim().isEmpty()) {
                    displayName = getContext().getString(R.string.anonymous_account);
                }
                text1.setText(displayName);

                StringBuilder details = new StringBuilder();
                if ((account.uUserType & UserType.USERTYPE_ADMIN) == UserType.USERTYPE_ADMIN) {
                    details.append(getContext().getString(R.string.user_type_admin));
                } else {
                    details.append(getContext().getString(R.string.user_type_default));
                }
                if (account.szLastModified != null && !account.szLastModified.isEmpty()) {
                    details.append(" | ").append(getContext().getString(R.string.last_modified, account.szLastModified));
                }
                if (account.szNote != null && !account.szNote.isEmpty()) {
                    details.append("\n").append(account.szNote);
                } else {
                    details.append("\n").append(getContext().getString(R.string.no_note));
                }
                text2.setText(details.toString());
            }
            return convertView;
        }
    }
}
