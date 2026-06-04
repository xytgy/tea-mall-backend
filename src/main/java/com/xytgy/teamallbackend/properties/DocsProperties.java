package com.xytgy.teamallbackend.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "docs")
@Data
public class DocsProperties {
    private boolean enabled = true;
}
