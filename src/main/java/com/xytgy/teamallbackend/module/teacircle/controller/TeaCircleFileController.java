package com.xytgy.teamallbackend.module.teacircle.controller;

import com.xytgy.teamallbackend.common.Result;
import com.xytgy.teamallbackend.common.ResultCode;
import com.xytgy.teamallbackend.security.SecurityUtils;
import com.xytgy.teamallbackend.exception.ServiceException;
import com.xytgy.teamallbackend.module.teacircle.vo.TeaCircleImageUploadVO;
import com.xytgy.teamallbackend.utils.AliyunOSSUtils;
import com.xytgy.teamallbackend.utils.FileMagicUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RestController
@Tag(name = "茶友圈 - 文件")
@RequestMapping("/api/tea-circle/files")
@SecurityRequirement(name = "BearerAuth")
@RequiredArgsConstructor
public class TeaCircleFileController {

    private static final int MAX_FILES = 9;
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final Set<String> ALLOWED_TYPES = new HashSet<>(Arrays.asList(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp"
    ));

    private final AliyunOSSUtils aliyunOSSUtils;

    @PostMapping("/images")
    @Operation(summary = "上传茶友圈图片(多文件)")
    public Result<TeaCircleImageUploadVO> uploadImages(@RequestParam("files") MultipartFile[] files) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new ServiceException(ResultCode.UNAUTHORIZED, "未登录");
        }
        if (files == null || files.length == 0) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "files 不能为空");
        }
        if (files.length > MAX_FILES) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "最多上传 " + MAX_FILES + " 张图片");
        }

        List<String> urls = new ArrayList<>(files.length);
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            if (file.getSize() > MAX_FILE_SIZE) {
                throw new ServiceException(ResultCode.BAD_REQUEST, "单张图片不能超过 10MB");
            }
            String contentType = file.getContentType();
            if (contentType == null || !ALLOWED_TYPES.contains(contentType.toLowerCase())) {
                throw new ServiceException(ResultCode.BAD_REQUEST, "仅支持 jpg/jpeg/png/webp");
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
            urls.add(aliyunOSSUtils.uploadTeaCircleImage(file));
        }

        return Result.success(new TeaCircleImageUploadVO(urls));
    }
}

