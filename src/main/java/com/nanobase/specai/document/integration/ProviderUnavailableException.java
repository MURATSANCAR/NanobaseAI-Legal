package com.nanobase.specai.document.integration;

public class ProviderUnavailableException extends RuntimeException {
    public ProviderUnavailableException(String message) {
        super(message);
    }

    public ProviderUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
