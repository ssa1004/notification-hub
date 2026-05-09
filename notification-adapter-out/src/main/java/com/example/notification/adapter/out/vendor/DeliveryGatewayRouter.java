package com.example.notification.adapter.out.vendor;

import com.example.notification.application.port.out.DeliveryGateway;
import com.example.notification.domain.channel.ChannelType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * channelType → 해당 vendor adapter 라우팅. Spring 이 모든 {@link DeliveryGateway} 구현체를
 * 모아서 주입하면 channelType 키로 indexing.
 *
 * <p>새 vendor 추가 시 이 클래스 수정 없이 새 {@code @Component} 구현체만 추가하면 자동 등록.
 */
@Component
public class DeliveryGatewayRouter {

    private final Map<ChannelType, DeliveryGateway> byType = new EnumMap<>(ChannelType.class);

    public DeliveryGatewayRouter(List<DeliveryGateway> gateways) {
        for (DeliveryGateway g : gateways) {
            ChannelType existing = g.channelType();
            if (byType.containsKey(existing)) {
                throw new IllegalStateException(
                        "duplicate DeliveryGateway for channel: " + existing);
            }
            byType.put(existing, g);
        }
    }

    public DeliveryGateway gatewayFor(ChannelType type) {
        DeliveryGateway g = byType.get(type);
        if (g == null) {
            throw new IllegalStateException("no DeliveryGateway for channel: " + type);
        }
        return g;
    }
}
