package com.example.notification.application.exception;

/** RecipientId 가 존재하지 않음. HTTP 404. */
public class RecipientNotFoundException extends ApplicationException {

    public RecipientNotFoundException(String recipientId) {
        super("recipient not found: " + recipientId);
    }
}
