package org.nekit.ttproplus.data;

import java.util.Vector;

import dk.bearware.ServerProperties;
import dk.bearware.TextMsgType;
import org.nekit.ttproplus.gui.AccessibilityAssistant;
import org.nekit.ttproplus.R;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class TextMessageAdapter extends BaseAdapter {

    private Vector<MyTextMessage> messages;
    private Vector<Integer> filteredIndices;

    private final LayoutInflater inflater;
    private final AccessibilityAssistant accessibilityAssistant;

    private ListView listView;
    private boolean autoScroll = true;

    private int myuserid;
    private boolean show_logs = true;

    int def_bg_color, def_text_color;

    int user_bg_color = 0xff4c9fff, user_text_color = Color.WHITE;
    int self_bg_color = 0xff659f5d, self_text_color = Color.WHITE;
    int loginfo_bg_color, loginfo_text_color;
    int logerr_bg_color = 0xffcd0028, logerr_text_color = Color.WHITE;
    int srvinfo_bg_color = Color.DKGRAY, srvinfo_text_color = Color.WHITE;

    private static final long THROTTLE_MS = 120;
    private long lastNotifyTime = 0;
    private boolean flushPending = false;
    private final Handler throttleHandler = new Handler(Looper.getMainLooper());
    private int lastKnownSize = 0;

    public TextMessageAdapter(Context context, AccessibilityAssistant accessibilityAssistant,
                              Vector<MyTextMessage> msgs, int myuserid) {
        this(context, accessibilityAssistant);
        setMyUserID(myuserid);
        setTextMessages(msgs);
    }

    @SuppressWarnings("ResourceType")
    public TextMessageAdapter(Context context, AccessibilityAssistant accessibilityAssistant) {
        inflater = LayoutInflater.from(context);
        this.accessibilityAssistant = accessibilityAssistant;
        messages = new Vector<>();
        filteredIndices = new Vector<>();

        TypedArray array = context.getTheme().obtainStyledAttributes(new int[] {
            android.R.attr.colorBackground,
            android.R.attr.textColorPrimary,
        });
        def_bg_color = array.getColor(0, 0xFF00FF);
        def_text_color = array.getColor(1, 0xFF00FF);

        array.recycle();

        loginfo_bg_color = def_bg_color;
        loginfo_text_color = def_text_color;
    }

    public void setListView(ListView lv) {
        listView = lv;
    }

    public void setAutoScroll(boolean scroll) {
        autoScroll = scroll;
    }

    public void setTextMessages(Vector<MyTextMessage> msgs) {
        this.messages = msgs;
        lastKnownSize = 0;
        rebuildFilter();
        super.notifyDataSetChanged();
    }

    private void rebuildFilter() {
        filteredIndices.clear();
        if(show_logs) {
            for(int i = 0; i < messages.size(); i++)
                filteredIndices.add(i);
        }
        else {
            for(int i = 0; i < messages.size(); i++) {
                MyTextMessage m = messages.get(i);
                switch(m.nMsgType) {
                    case MyTextMessage.MSGTYPE_LOG_ERROR :
                    case MyTextMessage.MSGTYPE_LOG_INFO :
                        break;
                    default :
                        filteredIndices.add(i);
                        break;
                }
            }
        }
        lastKnownSize = messages.size();
    }

    Vector<MyTextMessage> getRawMessages() {
        return messages;
    }

    public void setMyUserID(int userid) {
        myuserid = userid;
    }

    public void showLogMessages(boolean enable) {
        show_logs = enable;
        rebuildFilter();
        flushNow();
    }

    @Override
    public int getCount() {
        return filteredIndices.size();
    }

    @Override
    public Object getItem(int position) {
        return messages.get(filteredIndices.get(position));
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        MyTextMessage txtmsg = (MyTextMessage) getItem(position);

        int bg_color = Color.BLACK, text_color = Color.WHITE;

        switch(txtmsg.nMsgType) {
            case TextMsgType.MSGTYPE_CHANNEL :
            case TextMsgType.MSGTYPE_BROADCAST :
            case TextMsgType.MSGTYPE_USER : {
                if(convertView == null ||
                   convertView.findViewById(R.id.item_textmsg) == null)
                    convertView = inflater.inflate(R.layout.item_textmsg, parent, false);

                if(txtmsg.nFromUserID == myuserid) {
                    bg_color = self_bg_color;
                    text_color = self_text_color;
                }
                else {
                    bg_color = user_bg_color;
                    text_color = user_text_color;
                }

                TextView name = convertView.findViewById(R.id.name_text);
                TextView msgtext = convertView.findViewById(R.id.msg_text);
                TextView msgdate = convertView.findViewById(R.id.time_text);

                name.setText(txtmsg.szNickName);
                msgdate.setText(txtmsg.time.toString());
                msgtext.setText(txtmsg.szMessage);

                name.setTextColor(text_color);
                msgdate.setTextColor(text_color);
                msgtext.setTextColor(text_color);
                break;
            }
            case MyTextMessage.MSGTYPE_SERVERPROP : {
                if(convertView == null ||
                   convertView.findViewById(R.id.item_textmsg_srvinfo) == null) {
                    convertView = inflater.inflate(R.layout.item_textmsg_srvinfo, parent, false);
                }

                bg_color = srvinfo_bg_color;
                text_color = srvinfo_text_color;

                TextView logmsg = convertView.findViewById(R.id.srvname_text);
                TextView logmotd = convertView.findViewById(R.id.srvmotd_text);
                TextView logtm = convertView.findViewById(R.id.logtime_text);

                ServerProperties p = (ServerProperties)txtmsg.userData;
                logmsg.setText(p.szServerName);
                logmotd.setText(p.szMOTD);
                logtm.setText(txtmsg.time.toString());

                logmsg.setTextColor(text_color);
                logtm.setTextColor(text_color);
                break;
            }
            case MyTextMessage.MSGTYPE_LOG_ERROR :
            case MyTextMessage.MSGTYPE_LOG_INFO :
            default : {
                if(convertView == null ||
                   convertView.findViewById(R.id.item_textmsg_logmsg) == null) {
                    convertView = inflater.inflate(R.layout.item_textmsg_logmsg, parent, false);
                }

                switch(txtmsg.nMsgType) {
                    case MyTextMessage.MSGTYPE_LOG_ERROR :
                        bg_color = logerr_bg_color;
                        text_color = logerr_text_color;
                        break;
                    case MyTextMessage.MSGTYPE_LOG_INFO :
                        bg_color = loginfo_bg_color;
                        text_color = loginfo_text_color;
                        break;
                }

                TextView logmsg = convertView.findViewById(R.id.logmsg_text);
                TextView logtm = convertView.findViewById(R.id.logtime_text);

                logmsg.setText(txtmsg.szMessage);
                logtm.setText(txtmsg.time.toString());

                logmsg.setTextColor(text_color);
                logtm.setTextColor(text_color);
                break;
            }
        }

        convertView.setBackgroundColor(bg_color);
        convertView.setAccessibilityDelegate(accessibilityAssistant);

        return convertView;
    }

    private boolean isLogMsg(MyTextMessage m) {
        return m.nMsgType == MyTextMessage.MSGTYPE_LOG_ERROR ||
               m.nMsgType == MyTextMessage.MSGTYPE_LOG_INFO;
    }

    @Override
    public void notifyDataSetChanged() {
        if(messages.size() < lastKnownSize) {
            rebuildFilter();
        }
        else {
            for(int i = lastKnownSize; i < messages.size(); i++) {
                if(show_logs || !isLogMsg(messages.get(i)))
                    filteredIndices.add(i);
            }
            lastKnownSize = messages.size();
        }
        throttledNotify();
    }

    private void throttledNotify() {
        long now = SystemClock.uptimeMillis();
        if(now - lastNotifyTime >= THROTTLE_MS) {
            lastNotifyTime = now;
            flushPending = false;
            accessibilityAssistant.lockEvents();
            super.notifyDataSetChanged();
            if(autoScroll && listView != null && filteredIndices.size() > 0)
                listView.setSelection(filteredIndices.size() - 1);
            accessibilityAssistant.unlockEvents();
        }
        else if(!flushPending) {
            flushPending = true;
            throttleHandler.postAtTime(this::flushNow, lastNotifyTime + THROTTLE_MS);
        }
    }

    private void flushNow() {
        flushPending = false;
        lastNotifyTime = SystemClock.uptimeMillis();
        accessibilityAssistant.lockEvents();
        super.notifyDataSetChanged();
        if(autoScroll && listView != null && filteredIndices.size() > 0)
            listView.setSelection(filteredIndices.size() - 1);
        accessibilityAssistant.unlockEvents();
    }
}