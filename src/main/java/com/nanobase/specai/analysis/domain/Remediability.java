package com.nanobase.specai.analysis.domain;

public enum Remediability {
    HARD_BLOCKER,
    REMEDIABLE_BEFORE_BID,
    REMEDIABLE_BEFORE_CONTRACT,
    REMEDIABLE_AFTER_AWARD,
    REMEDIABLE_DURING_CONTRACT,
    NOT_APPLICABLE,
    UNKNOWN
}
