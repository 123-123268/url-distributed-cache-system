package com.kavya.url_shortner.domain.services;

import com.kavya.url_shortner.config.SnowflakeProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.function.LongSupplier;

/**
 * Thread-safe, per-process Snowflake ID generator.
 *
 * <p>IDs use 41 bits for milliseconds since 2024-01-01T00:00:00Z, 10 bits for
 * a worker ID, and 12 bits for a per-millisecond sequence. The high (sign) bit
 * remains unset, so all generated IDs fit in a positive PostgreSQL BIGINT.</p>
 */
@Component
public class SnowflakeIdGenerator {

    public static final long CUSTOM_EPOCH_MILLIS = Instant.parse("2024-01-01T00:00:00Z").toEpochMilli();
    public static final int TIMESTAMP_BITS = 41;
    public static final int WORKER_ID_BITS = 10;
    public static final int SEQUENCE_BITS = 12;
    public static final long MAX_WORKER_ID = (1L << WORKER_ID_BITS) - 1;
    public static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1;

    private final long workerId;
    private final LongSupplier currentTimeMillis;
    private long lastTimestamp = -1L;
    private long sequence;

    @Autowired
    public SnowflakeIdGenerator(SnowflakeProperties properties) {
        this(properties.workerId(), System::currentTimeMillis);
    }

    SnowflakeIdGenerator(long workerId, LongSupplier currentTimeMillis) {
        if (workerId < 0 || workerId > MAX_WORKER_ID) {
            throw new IllegalArgumentException("workerId must be between 0 and " + MAX_WORKER_ID);
        }
        this.workerId = workerId;
        this.currentTimeMillis = currentTimeMillis;
    }

    public synchronized long nextId() {
        long timestamp = currentTimestamp();

        if (timestamp < lastTimestamp) {
            throw clockMovedBackwards(lastTimestamp, timestamp);
        }

        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                timestamp = waitForNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0;
        }

        lastTimestamp = timestamp;
        return (timestamp << (WORKER_ID_BITS + SEQUENCE_BITS))
                | (workerId << SEQUENCE_BITS)
                | sequence;
    }

    private long currentTimestamp() {
        long timestamp = currentTimeMillis.getAsLong() - CUSTOM_EPOCH_MILLIS;
        if (timestamp < 0) {
            throw new IllegalStateException("Current time is before the Snowflake epoch");
        }
        if (timestamp >= (1L << TIMESTAMP_BITS)) {
            throw new IllegalStateException("Snowflake timestamp has exceeded its 41-bit range");
        }
        return timestamp;
    }

    private long waitForNextMillis(long previousTimestamp) {
        long timestamp;
        do {
            Thread.onSpinWait();
            timestamp = currentTimestamp();
            if (timestamp < previousTimestamp) {
                throw clockMovedBackwards(previousTimestamp, timestamp);
            }
        } while (timestamp <= previousTimestamp);
        return timestamp;
    }

    private IllegalStateException clockMovedBackwards(long expectedTimestamp, long actualTimestamp) {
        return new IllegalStateException(
                "Clock moved backwards by " + (expectedTimestamp - actualTimestamp)
                        + " ms; refusing to generate an ID"
        );
    }
}
