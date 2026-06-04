package com.xytgy.teamallbackend.module.upload.controller;

import com.xytgy.teamallbackend.common.Result;
import com.xytgy.teamallbackend.common.ResultCode;
import com.xytgy.teamallbackend.exception.ServiceException;
import com.xytgy.teamallbackend.utils.AliyunOSSUtils;
import com.xytgy.teamallbackend.utils.FileMagicUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

@Slf4j
@RestController
@RequestMapping("/api")
@Tag(name = "文件上传")
@SecurityRequirement(name = "BearerAuth")
@RequiredArgsConstructor
public class FileUploadController {

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp",
            "image/gif"
    );

    private final AliyunOSSUtils aliyunOSSUtils;

    @PostMapping("/upload")
    @Operation(summary = "通用图片文件上传接口")
    public Result<String> uploadImage(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "文件大小不能超过 10MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType.toLowerCase())) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "仅支持 jpg/jpeg/png/webp/gif 格式");
        }
        // 魔术字校验：检查文件头字节，防止伪造 Content-Type 上传恶意文件
        try {
            if (!FileMagicUtils.isImage(file.getInputStream())) {
                throw new ServiceException(ResultCode.BAD_REQUEST, "文件内容与声明的格式不符");
            }
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "文件校验失败");
        }

        log.info("文件开始上传, fileName={}, size={}", file.getOriginalFilename(), file.getSize());
        String url = aliyunOSSUtils.upload(file);
        return Result.success(url);
    }
}
