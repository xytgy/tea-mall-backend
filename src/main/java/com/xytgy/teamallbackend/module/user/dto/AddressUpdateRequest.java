package com.xytgy.teamallbackend.module.user.dto;

import lombok.Data;

@Data
public class AddressUpdateRequest {
    private Long id;
    private String receiverName;
    private String receiverPhone;
    private String receiverAddress;
    private Boolean isDefault;
}
