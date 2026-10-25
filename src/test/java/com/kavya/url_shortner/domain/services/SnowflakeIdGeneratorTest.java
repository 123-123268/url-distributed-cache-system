package com.kavya.url_shortner.domain.services;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnowflakeIdGeneratorTest {

    @Test
    void generatesUniqueIdsWithinOneInstance() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(7, System::currentTimeMillis);
        Set<Long> ids = new HashSet<>();

        for (int i = 0; i < 10_000; i++) {
            ids.add(generator.nextId());
        }

        assertEquals(10_000, ids.size());
    }

    @Test
    void isThreadSafeUnderConcurrentGeneration() throws InterruptedException {
        int threads = 16;
        int idsPerThread = 1_000;
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(7, System::currentTimeMillis);
        Set<Long> ids = ConcurrentHashMap.newKeySet();
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(threads);

        for (int thread = 0; thread < threads; thread++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    for (int i = 0; i < idsPerThread; i++) {
                        ids.add(generator.nextId());
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } finally {
                    finished.countDown();
                }
            });
        }

        assertTrue(ready.await(5, TimeUnit.SECONDS));
        start.countDown();
        assertTrue(finished.await(10, TimeUnit.SECONDS));
        executor.shutdownNow();

        assertEquals(threads * idsPerThread, ids.size());
    }

    @Test
    void waitsForTheNextMillisecondWhenTheSequenceRollsOver() {
        AtomicInteger calls = new AtomicInteger();
        long baseTime = SnowflakeIdGenerator.CUSTOM_EPOCH_MILLIS + 100;
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(
                7,
                () -> calls.incrementAndGet() <= 4_097 ? baseTime : baseTime + 1
        );
        Set<Long> ids = new HashSet<>();

        long rolloverId = 0;
        for (int i = 0; i < 4_097; i++) {
            rolloverId = generator.nextId();
            ids.add(rolloverId);
        }

        assertEquals(4_097, ids.size());
        assertEquals(101, rolloverId >>> (SnowflakeIdGenerator.WORKER_ID_BITS + SnowflakeIdGenerator.SEQUENCE_BITS));
        assertEquals(0, rolloverId & SnowflakeIdGenerator.MAX_SEQUENCE);
    }

    @Test
    void differentWorkerIdsProduceDifferentIdsForTheSameTimestampAndSequence() {
        long fixedTime = SnowflakeIdGenerator.CUSTOM_EPOCH_MILLIS + 100;
        long workerOneId = new SnowflakeIdGenerator(1, () -> fixedTime).nextId();
        long workerTwoId = new SnowflakeIdGenerator(2, () -> fixedTime).nextId();

        assertNotEquals(workerOneId, workerTwoId);
        assertEquals(1, (workerOneId >>> SnowflakeIdGenerator.SEQUENCE_BITS) & SnowflakeIdGenerator.MAX_WORKER_ID);
        assertEquals(2, (workerTwoId >>> SnowflakeIdGenerator.SEQUENCE_BITS) & SnowflakeIdGenerator.MAX_WORKER_ID);
    }

    @Test
    void rejectsClockRollbackToPreventDuplicateIds() {
        AtomicLong clock = new AtomicLong(SnowflakeIdGenerator.CUSTOM_EPOCH_MILLIS + 100);
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(7, clock::get);
        generator.nextId();
        clock.decrementAndGet();

        assertThrows(IllegalStateException.class, generator::nextId);
    }
}
