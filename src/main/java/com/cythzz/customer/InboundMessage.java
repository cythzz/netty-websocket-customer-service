package com.cythzz.customer;

public record InboundMessage(String type, String targetSessionId, String content) {
}
