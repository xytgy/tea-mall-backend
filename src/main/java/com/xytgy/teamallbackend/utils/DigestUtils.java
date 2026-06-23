package com.xytgy.teamallbackend.utils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 摘要/哈希工具方法。
 */
public final class DigestUtils {

    private DigestUtils() {}

    /**
     * 对输入字节数组计算 MD5 并返回 32 位小写十六进制字符串。
     *
     * @param data 输入数据
     * @return 32 位十六进制摘要
     */
    public static String md5Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(data);
            StringBuilder sb = new StringBuilder(32);
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // MD5 is guaranteed to be available in every JVM
            throw new IllegalStateException(e);
        }
    }
}
