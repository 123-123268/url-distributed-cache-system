package com.kavya.url_shortner.domain.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Base62EncoderTest {

    private final Base62Encoder encoder = new Base62Encoder();

    @Test
    void encodesIdsDeterministically() {
        assertEquals("0", encoder.encode(0));
        assertEquals("Z", encoder.encode(61));
        assertEquals("10", encoder.encode(62));
        assertEquals("8m0Kx", encoder.encode(123_456_789L));
    }
}
