package com.securetrade.menu;

import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

public class TradeSessionManager {
    private static final List<TradeSession> activeSessions = new ArrayList<>();

    public static synchronized void register(TradeSession session) {
        if (!activeSessions.contains(session)) {
            activeSessions.add(session);
        }
    }

    public static synchronized void unregister(TradeSession session) {
        activeSessions.remove(session);
    }

    public static synchronized void tick() {
        List<TradeSession> copy = new ArrayList<>(activeSessions);
        for (TradeSession session : copy) {
            session.tick();
        }
    }

    public static synchronized void cancelAllAndClear() {
        List<TradeSession> copy = new ArrayList<>(activeSessions);
        for (TradeSession session : copy) {
            session.cancelTrade();
        }
        activeSessions.clear();
    }

    public static synchronized void cancelForPlayer(ServerPlayer player) {
        List<TradeSession> copy = new ArrayList<>(activeSessions);
        for (TradeSession session : copy) {
            if (session.player1 == player || session.player2 == player) {
                session.cancelTrade();
            }
        }
    }
}
