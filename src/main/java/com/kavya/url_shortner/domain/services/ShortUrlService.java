package com.kavya.url_shortner.domain.services;

import com.kavya.url_shortner.ApplicationProperties;
import com.kavya.url_shortner.domain.entities.ShortUrl;
import com.kavya.url_shortner.domain.model.CreateShortUrlCmd;
import com.kavya.url_shortner.domain.model.PagedResult;
import com.kavya.url_shortner.domain.model.shortUrlDto;
import com.kavya.url_shortner.domain.repositories.ShortUrlRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

import static java.time.temporal.ChronoUnit.DAYS;

@Service
@Transactional(readOnly = true)
public class ShortUrlService {

    private final ApplicationProperties properties;
    private final ShortUrlRepository shortUrlRepository;
    private final EntityMapper entityMapper;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final Base62Encoder base62Encoder;

    public ShortUrlService(
            ApplicationProperties properties,
            ShortUrlRepository shortUrlRepository,
            EntityMapper entityMapper,
            SnowflakeIdGenerator snowflakeIdGenerator,
            Base62Encoder base62Encoder
    ) {
        this.properties = properties;
        this.shortUrlRepository = shortUrlRepository;
        this.entityMapper = entityMapper;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
        this.base62Encoder = base62Encoder;
    }

    public PagedResult<shortUrlDto> findAllPublicShortUrls(
            int pageNumber,
            int pageSize
    ) {
        Pageable pageable = getPageable(pageNumber, pageSize);

        Page<shortUrlDto> page = shortUrlRepository
                .findAllShortUrls(pageable)
                .map(entityMapper::toShortUrlDto);

        return PagedResult.from(page);
    }

    @Transactional
    public shortUrlDto createShortUrl(CreateShortUrlCmd command) {
        if (properties.validateOriginalUrl()
                && !UrlExistenceValidator.isUrlExists(command.originalUrl())) {
            throw new IllegalArgumentException(
                    "Invalid URL: " + command.originalUrl()
            );
        }

        ShortUrl shortUrl = new ShortUrl();
        long snowflakeId = snowflakeIdGenerator.nextId();
        shortUrl.setId(snowflakeId);
        shortUrl.setOriginalUrl(command.originalUrl());
        shortUrl.setShortKey(base62Encoder.encode(snowflakeId));
        shortUrl.setCreatedAt(Instant.now());

        ShortUrl savedShortUrl = shortUrlRepository.save(shortUrl);
        return entityMapper.toShortUrlDto(savedShortUrl);
    }

    public Optional<shortUrlDto> accessShortUrl(String shortKey) {
        return shortUrlRepository
                .findByShortKey(shortKey)
                .map(entityMapper::toShortUrlDto);
    }

    private Pageable getPageable(int page, int pageSize) {
        int pageIndex = page > 1 ? page - 1 : 0;

        return PageRequest.of(
                pageIndex,
                pageSize,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
    }
}
