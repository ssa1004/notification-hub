package com.example.notification.application.exception

/** RecipientId 가 존재하지 않음. HTTP 404. */
class RecipientNotFoundException(recipientId: String) :
    ApplicationException("recipient not found: $recipientId")
