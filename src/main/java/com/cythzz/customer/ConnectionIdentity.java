package com.cythzz.customer;

public record ConnectionIdentity(String sessionId, String userId, Role role) {
    public enum Role { CUSTOMER, AGENT }
}
