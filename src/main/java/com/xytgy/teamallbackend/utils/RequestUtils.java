package com.xytgy.teamallbackend.utils;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Set;

/**
 * HTTP 请求工具类，提供安全的客户端 IP 提取能力。
 * <p>
 * <b>防伪造策略：</b>
 * <ol>
 *   <li>优先使用 Nginx 覆盖写入的 X-Real-IP（Nginx 配置中应使用 proxy_set_header X-Real-IP $remote_addr;）</li>
 *   <li>其次检查 X-Forwarded-For，并验证其第一个 IP 是否来自可信代理；若请求未经可信代理转发则忽略该头</li>
 *   <li>最终回退到 HttpServletRequest#getRemoteAddr()</li>
 * </ol>
 * <p>
 * <b>Nginx 配置示例（防伪造关键）：</b>
 * <pre>
 * server {
 *     listen 80;
 *     server_name example.com;
 *
 *     location / {
 *         proxy_pass http://backend:8082;
 *         # 关键：用 $remote_addr 覆盖 X-Real-IP，防止客户端伪造
 *         proxy_set_header X-Real-IP $remote_addr;
 *         # 追加（而非覆盖）X-Forwarded-For，保留真实链路
 *         proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
 *         proxy_set_header Host $host;
 *     }
 * }
 * </pre>
 *
 * @see "application.yaml 中的 trusted.proxies 配置"
 */
public final class RequestUtils {

    private RequestUtils() {
        // 工具类不允许实例化
    }

    /**
     * 默认可信代理 IP 列表（仅本地回环地址）。
     * <p>
     * 生产环境应通过 {@code trusted.proxies} 配置项覆盖。
     */
    private static final Set<String> DEFAULT_TRUSTED_PROXIES = Set.of(
            "127.0.0.1",
            "0:0:0:0:0:0:0:1",
            "::1"
    );

    /**
     * 可信代理 IP 集合，由 {@link TrustedProxyConfig} 初始化。
     * <p>
     * 默认值仅信任本机，生产环境必须通过配置覆盖。
     */
    private static volatile Set<String> trustedProxies = DEFAULT_TRUSTED_PROXIES;

    /**
     * 设置可信代理 IP 列表（由 Spring 配置类在启动时调用）。
     *
     * @param proxies 可信代理 IP 集合
     */
    public static void setTrustedProxies(Set<String> proxies) {
        if (proxies != null && !proxies.isEmpty()) {
            trustedProxies = Set.copyOf(proxies);
        }
    }

    /**
     * 获取可信代理 IP 集合（只读视图）。
     *
     * @return 当前可信代理 IP 集合
     */
    public static Set<String> getTrustedProxies() {
        return trustedProxies;
    }

    /**
     * 获取真实客户端 IP 地址。
     * <p>
     * 解析优先级：
     * <ol>
     *   <li>Nginx 覆盖写入的 X-Real-IP（需 Nginx 配置 proxy_set_header X-Real-IP $remote_addr）</li>
     *   <li>X-Forwarded-For 的第一个 IP（仅当 remoteAddr 属于可信代理时才信任）</li>
     *   <li>HttpServletRequest#getRemoteAddr()</li>
     * </ol>
     *
     * @param request HTTP 请求
     * @return 客户端真实 IP 地址
     */
    public static String getClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();

        // 1. 优先使用 Nginx 设置的 X-Real-IP
        // Nginx 配置: proxy_set_header X-Real-IP $remote_addr;
        String ip = request.getHeader("X-Real-IP");
        if (isValidIp(ip)) {
            return ip;
        }

        // 2. 使用 X-Forwarded-For 的第一个 IP（仅当 remoteAddr 属于可信代理时）
        // X-Forwarded-For 格式：client, proxy1, proxy2
        // 只有当直连来源（remoteAddr）是可信代理时，才信任 XFF 头中的值
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty() && !"unknown".equalsIgnoreCase(xff)) {
            if (isTrustedProxy(remoteAddr)) {
                String firstIp = xff.split(",")[0].trim();
                if (isValidIp(firstIp)) {
                    return firstIp;
                }
            }
            // remoteAddr 不是可信代理 -> X-Forwarded-For 可能是客户端伪造的，忽略
        }

        // 3. 回退到 remoteAddr
        return remoteAddr;
    }

    /**
     * 判断 IP 地址是否属于可信代理。
     *
     * @param ip 待验证的 IP 地址
     * @return 如果属于可信代理返回 true
     */
    private static boolean isTrustedProxy(String ip) {
        return ip != null && trustedProxies.contains(ip);
    }

    /**
     * 校验 IP 字符串是否有效（非空、非 unknown）。
     *
     * @param ip IP 字符串
     * @return 有效返回 true
     */
    private static boolean isValidIp(String ip) {
        return ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip);
    }
}
