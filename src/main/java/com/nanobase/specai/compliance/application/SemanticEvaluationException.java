package com.nanobase.specai.compliance.application;

public class SemanticEvaluationException extends RuntimeException {
    private final SemanticEvaluationFailureCode failureCode;
    private final int retryAttempt;

    public SemanticEvaluationException(SemanticEvaluationFailureCode failureCode,
                                       String message, Throwable cause, int retryAttempt) {
        super(message, cause);
        this.failureCode = failureCode;
        this.retryAttempt = retryAttempt;
    }

    public SemanticEvaluationException(SemanticEvaluationFailureCode failureCode,
                                       String message, int retryAttempt) {
        this(failureCode, message, null, retryAttempt);
    }

    public SemanticEvaluationFailureCode failureCode() {
        return failureCode;
    }

    public int retryAttempt() {
        return retryAttempt;
    }
}
