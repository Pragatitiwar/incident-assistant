package com.demo.incidentAssistance.exception;

public class AiServiceException extends RuntimeException {
    public AiServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
