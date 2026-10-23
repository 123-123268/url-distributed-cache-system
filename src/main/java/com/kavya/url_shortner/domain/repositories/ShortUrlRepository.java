package com.kavya.url_shortner.domain.repositories;

import com.kavya.url_shortner.domain.entities.ShortUrl;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

    public interface ShortUrlRepository extends JpaRepository<ShortUrl, Long> {

        boolean existsByShortKey(String shortKey);

        Optional<ShortUrl> findByShortKey(String shortKey);

        @Query("select su from ShortUrl su order by su.createdAt desc")
        Page<ShortUrl> findAllShortUrls(Pageable pageable);

    }