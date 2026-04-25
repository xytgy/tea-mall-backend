package com.xytgy.teamallbackend.module.user.dto;

import lombok.Data;

@Data
public class AddressAddRequest {
    private String receiverName;
    private String receiverPhone;
    private String receiverAddress;
    private Boolean isDefault;
}
