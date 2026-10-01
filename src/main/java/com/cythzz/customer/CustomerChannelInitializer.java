package com.cythzz.customer;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.timeout.IdleStateHandler;

final class CustomerChannelInitializer extends ChannelInitializer<SocketChannel> {
    private final ObjectMapper objectMapper;
    private final SessionRegistry registry;

    CustomerChannelInitializer(ObjectMapper objectMapper, SessionRegistry registry) {
        this.objectMapper = objectMapper;
        this.registry = registry;
    }

    @Override
    protected void initChannel(SocketChannel channel) {
        channel.pipeline()
            .addLast(new HttpServerCodec())
            .addLast(new HttpObjectAggregator(64 * 1024))
            .addLast(new IdleStateHandler(65, 25, 0))
            .addLast(new HandshakeQueryHandler())
            .addLast(new WebSocketServerProtocolHandler("/ws", null, true, 64 * 1024))
            .addLast(new CustomerServiceWebSocketHandler(objectMapper, registry));
    }
}
