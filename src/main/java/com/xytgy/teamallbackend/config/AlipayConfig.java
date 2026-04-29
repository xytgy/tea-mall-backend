package com.xytgy.teamallbackend.config;

import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "alipay")
public class AlipayConfig {

    private String appId = "your_app_id";
    private String privateKey = "your_private_key";
    private String alipayPublicKey = "your_alipay_public_key";
    private String serverUrl = "https://openapi-sandbox.dl.alipaydev.com/gateway.do"; // 沙箱环境
    private String notifyUrl = "http://your_domain.com/api/payment/alipay/notify";
    private String returnUrl = "http://your_domain.com/pay/success";
    private String format = "json";
    private String charset = "UTF-8";
    private String signType = "RSA2";

    @Bean
    public AlipayClient alipayClient() {
        return new DefaultAlipayClient(serverUrl, appId, privateKey, format, charset, alipayPublicKey, signType);
    }
}