package com.securetrade.command;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class TradeRequestManager {
    public enum CreateStatus { CREATED, MUTUAL, SENDER_BUSY, TARGET_BUSY, COOLDOWN }

    public static final class Request {
        private final UUID senderId;
        private final UUID targetId;
        private final long expirationTime;

        private Request(UUID senderId, UUID targetId, long expirationTime) {
            this.senderId = senderId;
            this.targetId = targetId;
            this.expirationTime = expirationTime;
        }

        public UUID senderId() { return senderId; }
        public UUID targetId() { return targetId; }
        public long expirationTime() { return expirationTime; }
    }

    public static final class CreateResult {
        private final CreateStatus status;
        private final Request request;
        private final int cooldownSeconds;

        private CreateResult(CreateStatus status, Request request, int cooldownSeconds) {
            this.status = status;
            this.request = request;
            this.cooldownSeconds = cooldownSeconds;
        }

        public CreateStatus status() { return status; }
        public Request request() { return request; }
        public int cooldownSeconds() { return cooldownSeconds; }
    }

    private static final class CooldownKey {
        private final UUID sender;
        private final UUID target;

        private CooldownKey(UUID sender, UUID target) {
            this.sender = sender;
            this.target = target;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof CooldownKey)) return false;
            CooldownKey key = (CooldownKey) other;
            return sender.equals(key.sender) && target.equals(key.target);
        }

        @Override
        public int hashCode() {
            return 31 * sender.hashCode() + target.hashCode();
        }
    }

    private static final Map<UUID, Request> requestsByParticipant = new HashMap<UUID, Request>();
    private static final Map<CooldownKey, Long> cooldowns = new HashMap<CooldownKey, Long>();

    private TradeRequestManager() {}

    public static synchronized CreateResult create(UUID senderId, UUID targetId, long now, long timeoutMillis, long cooldownMillis) {
        prune(now, cooldownMillis);
        Request senderRequest = requestsByParticipant.get(senderId);
        if (senderRequest != null) {
            if (senderRequest.senderId.equals(targetId) && senderRequest.targetId.equals(senderId)) {
                remove(senderRequest);
                return new CreateResult(CreateStatus.MUTUAL, senderRequest, 0);
            }
            return new CreateResult(CreateStatus.SENDER_BUSY, senderRequest, 0);
        }
        Request targetRequest = requestsByParticipant.get(targetId);
        if (targetRequest != null) {
            return new CreateResult(CreateStatus.TARGET_BUSY, targetRequest, 0);
        }
        Long cooldownEnd = cooldowns.get(new CooldownKey(senderId, targetId));
        if (cooldownEnd != null && cooldownEnd.longValue() > now) {
            int seconds = (int) Math.ceil((cooldownEnd.longValue() - now) / 1000.0);
            return new CreateResult(CreateStatus.COOLDOWN, null, seconds);
        }
        Request request = new Request(senderId, targetId, now + timeoutMillis);
        requestsByParticipant.put(senderId, request);
        requestsByParticipant.put(targetId, request);
        return new CreateResult(CreateStatus.CREATED, request, 0);
    }

    public static synchronized Request takeIncoming(UUID targetId, long now, long cooldownMillis) {
        prune(now, cooldownMillis);
        Request request = requestsByParticipant.get(targetId);
        if (request == null || !request.targetId.equals(targetId)) return null;
        remove(request);
        return request;
    }

    public static synchronized boolean isMutualCandidate(UUID senderId, UUID targetId, long now, long cooldownMillis) {
        prune(now, cooldownMillis);
        Request request = requestsByParticipant.get(senderId);
        return request != null
                && request.senderId.equals(targetId)
                && request.targetId.equals(senderId);
    }

    public static synchronized void deny(Request request, long now, long cooldownMillis) {
        remove(request);
        if (cooldownMillis > 0) cooldowns.put(new CooldownKey(request.senderId, request.targetId), now + cooldownMillis);
    }

    public static synchronized void clearFor(UUID... participantIds) {
        for (UUID participantId : participantIds) {
            Request request = requestsByParticipant.get(participantId);
            if (request != null) remove(request);
        }
    }

    public static synchronized void clearAll() {
        requestsByParticipant.clear();
        cooldowns.clear();
    }

    public static synchronized void prune(long now, long cooldownMillis) {
        Set<Request> requests = new HashSet<Request>(requestsByParticipant.values());
        for (Request request : requests) {
            if (request.expirationTime <= now) {
                remove(request);
                if (cooldownMillis > 0) {
                    cooldowns.put(new CooldownKey(request.senderId, request.targetId), request.expirationTime + cooldownMillis);
                }
            }
        }
        cooldowns.entrySet().removeIf(entry -> entry.getValue().longValue() <= now);
    }

    private static void remove(Request request) {
        requestsByParticipant.remove(request.senderId, request);
        requestsByParticipant.remove(request.targetId, request);
    }
}
