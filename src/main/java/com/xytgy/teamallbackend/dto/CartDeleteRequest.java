package com.xytgy.teamallbackend.dto;

import lombok.Data;

import java.util.List;

@Data
public class CartDeleteRequest {
    private List<Long> ids;
}