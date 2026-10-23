package com.kavya.url_shortner.domain.model;

import java.io.Serializable;
import java.time.Instant;

/**
 * DTO for {@link com.kavya.url_shortner.domain.entities.ShortUrl}
 */
public record shortUrlDto(Long id, String shortKey, String originalUrl,
                          Instant createdAt) implements Serializable {
}