package com.xytgy.teamallbackend.mq.message.flashsale;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlashOrderCreateMessage {

    private String transactionId;
    private Long flashSaleId;
    private Long productId;
    private Long userId;
    private BigDecimal flashPrice;
}
