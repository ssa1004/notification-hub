package com.example.notification.application.exception

import java.util.UUID

class AttemptNotFoundException(id: UUID) : ApplicationException("deliveryAttempt not found: $id")
