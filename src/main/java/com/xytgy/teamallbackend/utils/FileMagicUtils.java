package com.xytgy.teamallbackend.utils;

import java.io.IOException;
import java.io.InputStream;

/**
 * 文件魔术字（Magic Bytes）校验工具。
 * 通过读取文件头部字节判断真实文件类型，防止攻击者伪造 Content-Type 上传恶意文件。
 */
public final class FileMagicUtils {

    private FileMagicUtils() {}

    /**
     * 校验文件内容是否为合法的图片格式（JPEG/PNG/GIF/WebP）
     *
     * @param input 文件输入流（方法执行后不关闭流，由调用方管理）
     * @return true 表示是合法图片，false 表示不是
     */
    public static boolean isImage(InputStream input) throws IOException {
        // 读取前 12 字节（WebP 需要检查到第 12 字节）
        byte[] header = new byte[12];
        int read = input.read(header);
        if (read < 4) {
            return false;
        }
        return isJpeg(header) || isPng(header) || isGif(header) || isWebp(header);
    }

    private static boolean isJpeg(byte[] h) {
        return h.length >= 3
                && (h[0] & 0xFF) == 0xFF
                && (h[1] & 0xFF) == 0xD8
                && (h[2] & 0xFF) == 0xFF;
    }

    private static boolean isPng(byte[] h) {
        return h.length >= 4
                && (h[0] & 0xFF) == 0x89
                && (h[1] & 0xFF) == 0x50
                && (h[2] & 0xFF) == 0x4E
                && (h[3] & 0xFF) == 0x47;
    }

    private static boolean isGif(byte[] h) {
        return h.length >= 4
                && (h[0] & 0xFF) == 0x47
                && (h[1] & 0xFF) == 0x49
                && (h[2] & 0xFF) == 0x46
                && (h[3] & 0xFF) == 0x38;
    }

    private static boolean isWebp(byte[] h) {
        return h.length >= 12
                && (h[0] & 0xFF) == 0x52  // R
                && (h[1] & 0xFF) == 0x49  // I
                && (h[2] & 0xFF) == 0x46  // F
                && (h[3] & 0xFF) == 0x46  // F
                && (h[8] & 0xFF) == 0x57  // W
                && (h[9] & 0xFF) == 0x45  // E
                && (h[10] & 0xFF) == 0x42 // B
                && (h[11] & 0xFF) == 0x50; // P
    }
}
