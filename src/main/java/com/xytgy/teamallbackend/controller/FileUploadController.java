package com.xytgy.teamallbackend.controller;

import com.xytgy.teamallbackend.common.Result;
import com.xytgy.teamallbackend.utils.AliyunOSSUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/api")
@Tag(name = "文件上传")
@SecurityRequirement(name = "BearerAuth")
@RequiredArgsConstructor
public class FileUploadController {

    private final AliyunOSSUtils aliyunOSSUtils;

    @PostMapping("/upload")
    @Operation(summary = "通用图片文件上传接口")
    public Result<String> uploadImage(@RequestParam("file") MultipartFile file) {
        log.info("文件开始上传");
        String url = aliyunOSSUtils.upload(file);
        return Result.success(url);

    }
}
