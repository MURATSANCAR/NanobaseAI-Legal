package com.nanobase.specai.document.integration;

public interface DocumentParserRouter {
    ParserRoute decide(DocumentRoutingContext context);
}
