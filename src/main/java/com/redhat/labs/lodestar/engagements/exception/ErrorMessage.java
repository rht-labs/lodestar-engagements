package com.redhat.labs.lodestar.engagements.exception;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
@AllArgsConstructor
public class ErrorMessage {
    
    String message;
    
    public ErrorMessage(String message, Object... substitutions) {
        this.message = String.format(message, substitutions);
    }
}
