package com.cythzz.customer;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;

import java.util.Map;

final class CustomerServiceWebSocketHandler extends SimpleChannelInboundHandler<WebSocketFrame> {
    private final ObjectMapper objectMapper;
    private final SessionRegistry registry;
    private final ServiceMetrics metrics;

    CustomerServiceWebSocketHandler(ObjectMapper objectMapper, SessionRegistry registry, ServiceMetrics metrics) {
        this.objectMapper = objectMapper;
        this.registry = registry;
        this.metrics = metrics;
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext context, Object event) throws Exception {
        if (event == WebSocketServerProtocolHandler.ServerHandshakeStateEvent.HANDSHAKE_COMPLETE) {
            registry.bind(context.channel().attr(HandshakeQueryHandler.IDENTITY).get(), context.channel());
            return;
        }
        if (event instanceof IdleStateEvent idleEvent) {
            if (idleEvent.state() == IdleState.WRITER_IDLE) {
                context.writeAndFlush(new PingWebSocketFrame());
                return;
            }
            if (idleEvent.state() == IdleState.READER_IDLE) {
                metrics.heartbeatTimeout();
                context.writeAndFlush(new CloseWebSocketFrame(1001, "heartbeat timeout"))
                    .addListener(future -> context.close());
                return;
            }
        }
        super.userEventTriggered(context, event);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext context, WebSocketFrame frame) throws Exception {
        if (frame instanceof PingWebSocketFrame ping) {
            context.writeAndFlush(new PongWebSocketFrame(ping.content().retain()));
            return;
        }
        if (frame instanceof PongWebSocketFrame) return;
        if (frame instanceof CloseWebSocketFrame close) {
            context.writeAndFlush(close.retain()).addListener(future -> context.close());
            return;
        }
        if (!(frame instanceof TextWebSocketFrame textFrame)) {
            registry.write(context.channel(), "ERROR", Map.of("message", "only text messages are supported"));
            return;
        }
        metrics.inboundMessage();
        InboundMessage message = objectMapper.readValue(textFrame.text(), InboundMessage.class);
        String type = message.type() == null ? "" : message.type().trim().toUpperCase();
        ConnectionIdentity identity = context.channel().attr(HandshakeQueryHandler.IDENTITY).get();
        switch (type) {
            case "HEARTBEAT" -> registry.write(context.channel(), "HEARTBEAT_ACK", Map.of());
            case "CHAT" -> registry.deliver(identity, message);
            case "CLAIM" -> handleClaim(context, identity, message);
            default -> registry.write(context.channel(), "ERROR", Map.of("message", "unknown message type"));
        }
    }

    private void handleClaim(ChannelHandlerContext context, ConnectionIdentity identity, InboundMessage message) {
        if (identity.role() != ConnectionIdentity.Role.AGENT) {
            registry.write(context.channel(), "ERROR", Map.of("message", "only agents can claim sessions"));
            return;
        }
        boolean claimed = registry.claim(message.targetSessionId(), identity.userId());
        registry.write(context.channel(), claimed ? "CLAIMED" : "SESSION_NOT_FOUND",
            Map.of("sessionId", message.targetSessionId() == null ? "" : message.targetSessionId()));
    }

    @Override
    public void channelInactive(ChannelHandlerContext context) throws Exception {
        ConnectionIdentity identity = context.channel().attr(HandshakeQueryHandler.IDENTITY).get();
        if (identity != null) registry.unbind(identity, context.channel());
        super.channelInactive(context);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
        registry.write(context.channel(), "ERROR", Map.of("message",
            cause.getMessage() == null ? "server error" : cause.getMessage()));
        context.close();
    }
}
