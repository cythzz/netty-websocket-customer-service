package com.cythzz.customer;

import io.micrometer.core.instrument.Counter;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;

final class ServiceMetrics {
    private final PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    private final Counter connections = registry.counter("customer_service_connections_total");
    private final Counter messages = registry.counter("customer_service_messages_total");
    private final Counter heartbeatTimeouts = registry.counter("customer_service_heartbeat_timeouts_total");
    private final Counter handoffs = registry.counter("customer_service_handoffs_total");

    void connected() {
        connections.increment();
    }

    void inboundMessage() {
        messages.increment();
    }

    void heartbeatTimeout() {
        heartbeatTimeouts.increment();
    }

    void handoffRequired() {
        handoffs.increment();
    }

    String scrape() {
        return registry.scrape();
    }
}
