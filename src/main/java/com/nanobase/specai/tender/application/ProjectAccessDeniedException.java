package com.nanobase.specai.tender.application;

public class ProjectAccessDeniedException extends RuntimeException {
    public ProjectAccessDeniedException() {
        super("The project is not available to the authenticated user");
    }
}
