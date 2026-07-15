package dk.bearware.events;

import java.util.Map;
import java.util.Vector;

import dk.bearware.ServerStatistics;
import dk.bearware.TTMessage;
import dk.bearware.events.TeamTalkEventHandler;

public class ServerStatsHelper {

    public interface OnServerStatisticsListener {
        void onServerStatistics(ServerStatistics stats);
    }

    private static final int CLIENTEVENT_CMD_SERVERSTATISTICS = 360;

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void registerListener(TeamTalkEventHandler handler, OnServerStatisticsListener listener) {
        TeamTalkEventHandler.ProcessTTMessage ptm = handler.new ProcessTTMessage(listener) {
            @Override
            void processTTMessage(TTMessage msg) {
                listener.onServerStatistics(msg.serverstatistics);
            }
        };

        Map listeners = handler.listeners;
        Vector v = (Vector) listeners.get(CLIENTEVENT_CMD_SERVERSTATISTICS);
        if (v == null) {
            v = new Vector();
            listeners.put(CLIENTEVENT_CMD_SERVERSTATISTICS, v);
        }
        v.add(ptm);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void unregisterListener(TeamTalkEventHandler handler, OnServerStatisticsListener listener) {
        Map listeners = handler.listeners;
        Vector v = (Vector) listeners.get(CLIENTEVENT_CMD_SERVERSTATISTICS);
        if (v != null) {
            v.removeIf(o -> {
                if (o instanceof TeamTalkEventHandler.ProcessTTMessage) {
                    return ((TeamTalkEventHandler.ProcessTTMessage) o).o == listener;
                }
                return false;
            });
        }
    }
}
