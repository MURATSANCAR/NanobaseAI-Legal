package com.nanobase.specai.tender.application;

import java.util.UUID;

public class TenderNotFoundException extends RuntimeException {
    public TenderNotFoundException(UUID id) {
        super("Tender project %s was not found".formatted(id));
    }
}
