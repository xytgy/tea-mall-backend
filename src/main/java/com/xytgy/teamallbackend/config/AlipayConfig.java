package com.xytgy.teamallbackend.config;

import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import lombok.Data;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Data
@Configuration
@Validated
@ConditionalOnProperty(prefix = "alipay", name = "enabled", havingValue = "true")
@ConfigurationProperties(prefix = "alipay")
public class AlipayConfig {

    private boolean enabled = false;

    @NotBlank(message = "alipay.app-id 不能为空")
    private String appId;

    @NotBlank(message = "alipay.private-key 不能为空")
    @Size(min = 512, message = "alipay.private-key 长度过短，请确认填入 RSA2 的 PKCS8 私钥")
    private String privateKey;

    @NotBlank(message = "alipay.alipay-public-key 不能为空")
    private String alipayPublicKey;

    @NotBlank(message = "alipay.server-url 不能为空")
    private String serverUrl;

    @NotBlank(message = "alipay.notify-url 不能为空")
    private String notifyUrl;

    @NotBlank(message = "alipay.return-url 不能为空")
    private String returnUrl;
    private String format = "json";
    private String charset = "UTF-8";
    private String signType = "RSA2";

    @Bean
    public AlipayClient alipayClient() {
        return new DefaultAlipayClient(serverUrl, appId, privateKey, format, charset, alipayPublicKey, signType);
    }
}
