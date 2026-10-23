package com.kavya.url_shortner.web.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateShortUrlForm(
        @NotBlank(message = "URL is required")
        @Size(max = 2048, message = "URL is too long")
        @Pattern(
                regexp = "https?://.+",
                message = "URL must start with http:// or https://"
        )
        String originalUrl
) {
}