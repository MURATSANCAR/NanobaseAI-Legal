package com.nanobase.specai.shared.security;

public class MissingTenantException extends RuntimeException {
    public MissingTenantException(String message) {
        super(message);
    }
}
