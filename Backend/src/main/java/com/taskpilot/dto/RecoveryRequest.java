package com.taskpilot.dto;

public class RecoveryRequest {

    private String message;

    public RecoveryRequest() {}

    public RecoveryRequest(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
