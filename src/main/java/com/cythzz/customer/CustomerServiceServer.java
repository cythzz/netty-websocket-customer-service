package com.cythzz.customer;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CustomerServiceServer {
    private static final Logger log = LoggerFactory.getLogger(CustomerServiceServer.class);
    private CustomerServiceServer() {
    }

    public static void main(String[] args) throws InterruptedException {
        int port = Integer.parseInt(System.getenv().getOrDefault("WS_PORT", "8080"));
        ObjectMapper objectMapper = new ObjectMapper();
        ServiceMetrics metrics = new ServiceMetrics();
        SessionRegistry registry = new SessionRegistry(objectMapper, metrics);
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            Channel server = new ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new CustomerChannelInitializer(objectMapper, registry, metrics))
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .bind(port).sync().channel();
            log.info("customer_service_server_started port={} websocketPath=/ws", port);
            server.closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}
