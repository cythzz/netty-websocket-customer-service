package com.cythzz.customer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class SessionRegistry {
    private final ObjectMapper objectMapper;
    private final Map<String, Channel> channelsBySession = new ConcurrentHashMap<>();
    private final Map<String, Channel> channelsByUser = new ConcurrentHashMap<>();
    private final Map<String, CustomerSession> customerSessions = new ConcurrentHashMap<>();

    SessionRegistry(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    void bind(ConnectionIdentity identity, Channel channel) {
        Channel previousUserChannel = channelsByUser.put(identity.userId(), channel);
        if (previousUserChannel != null && previousUserChannel != channel) {
            write(previousUserChannel, "REPLACED", Map.of("reason", "same user reconnected"));
            previousUserChannel.close();
        }
        Channel previousSessionChannel = channelsBySession.put(identity.sessionId(), channel);
        if (previousSessionChannel != null && previousSessionChannel != channel) {
            previousSessionChannel.close();
        }
        if (identity.role() == ConnectionIdentity.Role.CUSTOMER) {
            customerSessions.computeIfAbsent(identity.sessionId(), key -> new CustomerSession());
        }
        write(channel, "CONNECTED", Map.of(
            "sessionId", identity.sessionId(), "userId", identity.userId(), "role", identity.role().name()
        ));
    }

    void unbind(ConnectionIdentity identity, Channel channel) {
        channelsBySession.remove(identity.sessionId(), channel);
        channelsByUser.remove(identity.userId(), channel);
    }

    boolean claim(String customerSessionId, String agentUserId) {
        CustomerSession session = customerSessions.get(customerSessionId);
        if (session == null) return false;
        session.agentUserId = agentUserId;
        Channel customer = channelsBySession.get(customerSessionId);
        if (customer != null) write(customer, "AGENT_ASSIGNED", Map.of("agentUserId", agentUserId));
        return true;
    }

    void deliver(ConnectionIdentity sender, InboundMessage message) {
        if (sender.role() == ConnectionIdentity.Role.CUSTOMER) deliverCustomerMessage(sender, message);
        else deliverAgentMessage(sender, message);
    }

    Channel channelForSession(String sessionId) {
        return channelsBySession.get(sessionId);
    }

    private void deliverCustomerMessage(ConnectionIdentity sender, InboundMessage message) {
        CustomerSession session = customerSessions.get(sender.sessionId());
        String agentId = session == null ? null : session.agentUserId;
        Channel agentChannel = agentId == null ? null : channelsByUser.get(agentId);
        if (agentChannel == null || !agentChannel.isActive()) {
            write(channelsBySession.get(sender.sessionId()), "HANDOFF_REQUIRED", Map.of(
                "sessionId", sender.sessionId(), "message", "暂无在线客服，消息已进入人工排队队列"
            ));
            return;
        }
        write(agentChannel, "CHAT", Map.of(
            "sessionId", sender.sessionId(), "fromUserId", sender.userId(), "content", safe(message.content())
        ));
    }

    private void deliverAgentMessage(ConnectionIdentity sender, InboundMessage message) {
        if (message.targetSessionId() == null || message.targetSessionId().isBlank()) {
            write(channelsByUser.get(sender.userId()), "ERROR", Map.of("message", "targetSessionId is required"));
            return;
        }
        Channel customer = channelsBySession.get(message.targetSessionId());
        if (customer == null || !customer.isActive()) {
            write(channelsByUser.get(sender.userId()), "CUSTOMER_OFFLINE", Map.of("sessionId", message.targetSessionId()));
            return;
        }
        write(customer, "CHAT", Map.of(
            "sessionId", message.targetSessionId(), "fromUserId", sender.userId(), "content", safe(message.content())
        ));
    }

    void write(Channel channel, String type, Map<String, ?> payload) {
        if (channel == null || !channel.isActive()) return;
        try {
            channel.writeAndFlush(new TextWebSocketFrame(objectMapper.writeValueAsString(Map.of(
                "type", type, "timestamp", Instant.now().toString(), "payload", payload
            ))));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize WebSocket event", exception);
        }
    }

    private static String safe(String content) {
        return content == null ? "" : content.trim();
    }

    private static final class CustomerSession {
        private volatile String agentUserId;
    }
}
