package com.xytgy.teamallbackend.module.teacircle.dto;

import lombok.Data;
import java.util.List;

@Data
public class TeaPostAddRequest {
    private String content;
    private List<String> images;
}
