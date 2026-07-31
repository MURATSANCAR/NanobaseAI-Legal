package com.nanobase.specai.document.table;

public interface TableRequirementExtractionStrategy {
    TableRequirementExtractionResult extract(CanonicalTable table, TableExtractionProfile profile);
}
