package com.nanobase.specai.document.capability;

public interface DocumentProcessingRouter {
    DocumentProcessingPlan resolve(
        DocumentCapabilityProfile profile,
        DocumentProcessingPolicyVersion policy
    );
}
