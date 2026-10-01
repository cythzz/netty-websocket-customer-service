package com.cythzz.customer;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionRegistryTests {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SessionRegistry registry = new SessionRegistry(objectMapper);

    @Test
    void reconnectReplacesTheOldChannel() {
        var identity = new ConnectionIdentity("session-1", "customer-1", ConnectionIdentity.Role.CUSTOMER);
        EmbeddedChannel oldChannel = new EmbeddedChannel();
        EmbeddedChannel newChannel = new EmbeddedChannel();
        registry.bind(identity, oldChannel);
        registry.bind(identity, newChannel);
        assertSame(newChannel, registry.channelForSession("session-1"));
        assertTrue(!oldChannel.isActive());
    }

    @Test
    void messageIsQueuedUntilAnAgentClaimsTheSession() throws Exception {
        var customer = new ConnectionIdentity("session-2", "customer-2", ConnectionIdentity.Role.CUSTOMER);
        EmbeddedChannel customerChannel = new EmbeddedChannel();
        registry.bind(customer, customerChannel);
        ((TextWebSocketFrame) customerChannel.readOutbound()).release();
        registry.deliver(customer, new InboundMessage("CHAT", null, "我的订单什么时候发货？"));
        TextWebSocketFrame frame = customerChannel.readOutbound();
        assertEquals("HANDOFF_REQUIRED", objectMapper.readTree(frame.text()).get("type").asText());
        frame.release();
    }

    @Test
    void claimedSessionRoutesCustomerMessageToAgent() throws Exception {
        var customer = new ConnectionIdentity("session-3", "customer-3", ConnectionIdentity.Role.CUSTOMER);
        var agent = new ConnectionIdentity("agent-console", "agent-7", ConnectionIdentity.Role.AGENT);
        EmbeddedChannel customerChannel = new EmbeddedChannel();
        EmbeddedChannel agentChannel = new EmbeddedChannel();
        registry.bind(customer, customerChannel);
        registry.bind(agent, agentChannel);
        ((TextWebSocketFrame) customerChannel.readOutbound()).release();
        ((TextWebSocketFrame) agentChannel.readOutbound()).release();
        assertTrue(registry.claim("session-3", "agent-7"));
        ((TextWebSocketFrame) customerChannel.readOutbound()).release();
        registry.deliver(customer, new InboundMessage("CHAT", null, "需要人工客服"));
        TextWebSocketFrame frame = agentChannel.readOutbound();
        assertEquals("CHAT", objectMapper.readTree(frame.text()).get("type").asText());
        frame.release();
    }
}
