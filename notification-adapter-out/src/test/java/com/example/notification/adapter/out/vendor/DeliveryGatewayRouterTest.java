package com.example.notification.adapter.out.vendor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.notification.application.port.out.DeliveryGateway;
import com.example.notification.domain.channel.ChannelType;
import com.example.notification.domain.delivery.DeliveryAttempt;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeliveryGatewayRouterTest {

    @Test
    void routes_by_channel_type() {
        DeliveryGateway push = stub(ChannelType.PUSH);
        DeliveryGateway email = stub(ChannelType.EMAIL);
        DeliveryGatewayRouter router = new DeliveryGatewayRouter(List.of(push, email));
        assertThat(router.gatewayFor(ChannelType.PUSH)).isSameAs(push);
        assertThat(router.gatewayFor(ChannelType.EMAIL)).isSameAs(email);
    }

    @Test
    void duplicate_channel_rejected_at_construction() {
        DeliveryGateway p1 = stub(ChannelType.PUSH);
        DeliveryGateway p2 = stub(ChannelType.PUSH);
        assertThatThrownBy(() -> new DeliveryGatewayRouter(List.of(p1, p2)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void unknown_channel_throws() {
        DeliveryGatewayRouter router = new DeliveryGatewayRouter(List.of(stub(ChannelType.PUSH)));
        assertThatThrownBy(() -> router.gatewayFor(ChannelType.SMS))
                .isInstanceOf(IllegalStateException.class);
    }

    private static DeliveryGateway stub(ChannelType type) {
        return new DeliveryGateway() {
            @Override
            public ChannelType channelType() {
                return type;
            }

            @Override
            public String dispatch(DeliveryAttempt attempt) {
                return "msg";
            }
        };
    }
}
