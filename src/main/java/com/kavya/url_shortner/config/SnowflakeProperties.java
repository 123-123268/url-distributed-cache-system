package com.kavya.url_shortner.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "app.snowflake")
@Validated
public record SnowflakeProperties(
        @Min(0)
        @Max(1023)
        long workerId
) {
}
