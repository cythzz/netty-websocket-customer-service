package com.cythzz.customer;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.AttributeKey;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static io.netty.buffer.Unpooled.copiedBuffer;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static java.nio.charset.StandardCharsets.UTF_8;

final class HandshakeQueryHandler extends ChannelInboundHandlerAdapter {
    static final AttributeKey<ConnectionIdentity> IDENTITY = AttributeKey.valueOf("customer.identity");

    @Override
    public void channelRead(ChannelHandlerContext context, Object message) {
        if (!(message instanceof FullHttpRequest request)) {
            context.fireChannelRead(message);
            return;
        }
        QueryStringDecoder decoder = new QueryStringDecoder(request.uri());
        if (!"/ws".equals(decoder.path())) {
            reject(context, request, BAD_REQUEST, "WebSocket endpoint is /ws");
            return;
        }
        Map<String, List<String>> parameters = decoder.parameters();
        String sessionId = first(parameters, "sessionId");
        String userId = first(parameters, "userId");
        String roleValue = first(parameters, "role");
        if (sessionId == null || userId == null || roleValue == null) {
            reject(context, request, BAD_REQUEST, "sessionId, userId and role are required");
            return;
        }
        try {
            var role = ConnectionIdentity.Role.valueOf(roleValue.toUpperCase(Locale.ROOT));
            context.channel().attr(IDENTITY).set(new ConnectionIdentity(sessionId, userId, role));
            request.setUri("/ws");
            context.fireChannelRead(message);
        } catch (IllegalArgumentException exception) {
            reject(context, request, BAD_REQUEST, "role must be CUSTOMER or AGENT");
        }
    }

    private static String first(Map<String, List<String>> parameters, String key) {
        List<String> values = parameters.get(key);
        return values == null || values.isEmpty() || values.get(0).isBlank() ? null : values.get(0).trim();
    }

    private static void reject(ChannelHandlerContext context, FullHttpRequest request,
                               HttpResponseStatus status, String reason) {
        var response = new DefaultFullHttpResponse(HTTP_1_1, status, copiedBuffer(reason, UTF_8));
        response.headers().setInt(CONTENT_LENGTH, response.content().readableBytes());
        request.release();
        context.writeAndFlush(response).addListener(future -> context.close());
    }
}
