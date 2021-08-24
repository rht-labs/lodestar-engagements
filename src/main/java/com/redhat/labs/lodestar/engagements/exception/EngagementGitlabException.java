package com.redhat.labs.lodestar.engagements.exception;

public class EngagementGitlabException extends RuntimeException {
    
    private static final long serialVersionUID = 1L;
    
    private final int statusCode;

    public EngagementGitlabException(int statusCode, String message) {
        super(String.format("Status %d Reason %s", statusCode, message));
        this.statusCode = statusCode;
    }
    
    public EngagementGitlabException(int statusCode, String message, String details) {
        super(String.format("Status %d Reason %s Details %s", statusCode, message, details));
        this.statusCode = statusCode;
    }
    
    public EngagementGitlabException(int statusCode, String message, Exception ex) {
        super(String.format("Status %d Reason %s", statusCode, message), ex);
        this.statusCode = statusCode;
    }
    
    public int getStatusCode() {
        return statusCode;
    }
}
