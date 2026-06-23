package com.xytgy.teamallbackend.mq.message.order;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentSuccessMessage {

    private Long orderId;
    private Long userId;
    private Long paymentId;
}
