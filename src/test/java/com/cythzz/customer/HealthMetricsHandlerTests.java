package com.cythzz.customer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.Test;

class HealthMetricsHandlerTests {
    @Test
    void servesHealthAndPrometheusMetrics() {
        ServiceMetrics metrics = new ServiceMetrics();
        metrics.connected();
        EmbeddedChannel channel = new EmbeddedChannel(new HealthMetricsHandler(metrics));

        channel.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/health"));
        FullHttpResponse health = channel.readOutbound();
        assertEquals(200, health.status().code());
        assertEquals("{\"status\":\"UP\"}", health.content().toString(java.nio.charset.StandardCharsets.UTF_8));
        health.release();

        channel.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/metrics"));
        FullHttpResponse prometheus = channel.readOutbound();
        assertTrue(prometheus.content().toString(java.nio.charset.StandardCharsets.UTF_8)
                .contains("customer_service_connections_total"));
        prometheus.release();
        channel.finishAndReleaseAll();
    }
}
