package com.nanobase.specai.document.table;

public record TableExtractionProfile(
    String profileCode,
    boolean requireHeaderContext,
    int maxRows
) {
}
