package com.xytgy.teamallbackend.config;

import com.google.code.kaptcha.Producer;
import com.google.code.kaptcha.impl.DefaultKaptcha;
import com.google.code.kaptcha.util.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

@Configuration
public class KaptchaConfig {

    @Bean
    public Producer kaptchaProducer() {
        Properties props = new Properties();
        // 图片宽高
        props.setProperty("kaptcha.image.width", "120");
        props.setProperty("kaptcha.image.height", "40");
        // 字符数量
        props.setProperty("kaptcha.textproducer.char.length", "4");
        // 字体大小
        props.setProperty("kaptcha.textproducer.font.size", "32");
        // 干扰线
        props.setProperty("kaptcha.noise.count", "3");
        // 文字间距
        props.setProperty("kaptcha.textproducer.char.space", "5");

        //只生成数字
        props.setProperty("kaptcha.textproducer.char.string", "0123456789");
        DefaultKaptcha kaptcha = new DefaultKaptcha();
        kaptcha.setConfig(new Config(props));
        return kaptcha;
    }
}
