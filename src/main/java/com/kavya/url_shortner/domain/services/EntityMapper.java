package com.kavya.url_shortner.domain.services;

import com.kavya.url_shortner.domain.entities.ShortUrl;
import com.kavya.url_shortner.domain.model.shortUrlDto;
import org.springframework.stereotype.Component;

@Component
public class EntityMapper {

    public shortUrlDto toShortUrlDto(ShortUrl shortUrl) {
        return new shortUrlDto(
                shortUrl.getId(),
                shortUrl.getShortKey(),
                shortUrl.getOriginalUrl(),
                shortUrl.getCreatedAt()
        );
    }
}