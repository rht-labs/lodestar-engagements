package com.redhat.labs.lodestar.engagements.exception;

public class EngagementException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    
    public EngagementException(String message) {
        super(message);
    }
    
    public EngagementException(String message, Exception ex) {
        super(message, ex);
    }

}
