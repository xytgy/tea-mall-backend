package com.xytgy.teamallbackend.controller;

import com.xytgy.teamallbackend.common.Result;
import com.xytgy.teamallbackend.common.ResultCode;
import com.xytgy.teamallbackend.common.UserContext;
import com.xytgy.teamallbackend.exception.ServiceException;
import com.xytgy.teamallbackend.utils.AliyunOSSUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api")
@Tag(name = "文件上传")
@SecurityRequirement(name = "BearerAuth")
public class FileUploadController {

    @Autowired
    private AliyunOSSUtils aliyunOSSUtils;
    // 可以在 application.yaml 中配置上传目录，默认在项目根目录的 uploads 文件夹下
    @Value("${upload.dir:uploads/images/}")
    private String uploadDir;

    @PostMapping("/upload")
    @Operation(summary = "通用图片文件上传接口")
    public Result<String> uploadImage(@RequestParam("file") MultipartFile file, HttpServletRequest request) {
        log.info("文件开始上传");
        String url = aliyunOSSUtils.upload(file);
        return Result.success(url);

    }
}
