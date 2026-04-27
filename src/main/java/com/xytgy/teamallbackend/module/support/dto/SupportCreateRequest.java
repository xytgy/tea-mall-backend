package com.xytgy.teamallbackend.module.support.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SupportCreateRequest {
    @NotBlank(message = "咨询分类不能为空")
    private String category;
    
    @NotBlank(message = "咨询标题不能为空")
    private String title;
    
    @NotBlank(message = "咨询详细内容不能为空")
    private String content;
    
    private String contact;
}