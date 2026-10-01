package com.cythzz.customer;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.CharsetUtil;

final class HealthMetricsHandler extends ChannelInboundHandlerAdapter {
    private final ServiceMetrics metrics;

    HealthMetricsHandler(ServiceMetrics metrics) {
        this.metrics = metrics;
    }

    @Override
    public void channelRead(ChannelHandlerContext context, Object message) {
        if (!(message instanceof FullHttpRequest request)) {
            context.fireChannelRead(message);
            return;
        }
        String path = new QueryStringDecoder(request.uri()).path();
        if (!path.equals("/health") && !path.equals("/metrics")) {
            context.fireChannelRead(message);
            return;
        }

        String body = path.equals("/health") ? "{\"status\":\"UP\"}" : metrics.scrape();
        String contentType = path.equals("/health")
                ? "application/json; charset=UTF-8"
                : "text/plain; version=0.0.4; charset=UTF-8";
        var content = Unpooled.copiedBuffer(body, CharsetUtil.UTF_8);
        var response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, content);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType);
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());
        boolean keepAlive = HttpUtil.isKeepAlive(request);
        HttpUtil.setKeepAlive(response, keepAlive);
        request.release();
        context.writeAndFlush(response).addListener(future -> {
            if (!keepAlive) {
                context.close();
            }
        });
    }
}
