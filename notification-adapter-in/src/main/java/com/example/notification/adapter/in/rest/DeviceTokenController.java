package com.example.notification.adapter.in.rest;

import com.example.notification.adapter.in.rest.dto.RegisterDeviceTokenRequest;
import com.example.notification.application.port.in.RegisterDeviceTokenUseCase;
import com.example.notification.domain.device.DeviceToken;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/devices")
@RequiredArgsConstructor
public class DeviceTokenController {

    private final RegisterDeviceTokenUseCase registerUseCase;

    @PostMapping
    public ResponseEntity<DeviceTokenResponse> register(
            @Valid @RequestBody RegisterDeviceTokenRequest request) {
        DeviceToken d = registerUseCase.register(request.toCommand());
        return ResponseEntity.ok(DeviceTokenResponse.from(d));
    }

    public record DeviceTokenResponse(
            UUID id,
            String recipientId,
            String platform,
            boolean active) {
        static DeviceTokenResponse from(DeviceToken d) {
            return new DeviceTokenResponse(
                    d.id(),
                    d.recipientId().value(),
                    d.platform().name(),
                    d.isActive());
        }
    }
}
