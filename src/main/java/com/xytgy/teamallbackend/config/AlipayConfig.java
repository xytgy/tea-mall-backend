package com.xytgy.teamallbackend.config;

import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import lombok.Data;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Data
@Configuration
@ConfigurationProperties(prefix = "alipay")
public class AlipayConfig implements InitializingBean {

    private String appId;
    private String privateKey;
    private String alipayPublicKey;
    private String serverUrl;
    private String notifyUrl;
    private String returnUrl;
    private String format = "json";
    private String charset = "UTF-8";
    private String signType = "RSA2";

    @Bean
    public AlipayClient alipayClient() {
        return new DefaultAlipayClient(serverUrl, appId, privateKey, format, charset, alipayPublicKey, signType);
    }

    @Override
    public void afterPropertiesSet() {
        if (!StringUtils.hasText(appId) || !StringUtils.hasText(privateKey) || !StringUtils.hasText(alipayPublicKey) || !StringUtils.hasText(serverUrl)) {
            throw new IllegalStateException("Alipay 配置缺失：请在 application-dev.yaml 中配置 alipay.app-id/alipay.private-key/alipay.alipay-public-key/alipay.server-url");
        }
        if (privateKey.trim().length() < 512) {
            throw new IllegalStateException("Alipay privateKey 格式不正确：需要 RSA2 的 PKCS8 私钥（长度通常 > 1000），请检查是否填成了占位符或包含换行/头尾标识");
        }
    }
}
