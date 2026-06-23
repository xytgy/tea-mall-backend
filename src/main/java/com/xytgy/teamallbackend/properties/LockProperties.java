package com.xytgy.teamallbackend.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "lock")
@Data
public class LockProperties {
    private String keyPrefix = "lock:";
    private long defaultWaitMs = 100;
    private long defaultLeaseMs = 10_000;
    private String failMessage = "请勿重复提交";
}
