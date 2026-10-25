package com.kavya.url_shortner.domain.services;

import org.springframework.stereotype.Component;

/** Converts non-negative numeric IDs into compact, URL-safe Base62 strings. */
@Component
public class Base62Encoder {

    private static final char[] ALPHABET =
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
    private static final int BASE = ALPHABET.length;

    public String encode(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("Base62 only supports non-negative values");
        }
        if (value == 0) {
            return "0";
        }

        StringBuilder encoded = new StringBuilder();
        while (value > 0) {
            encoded.append(ALPHABET[(int) (value % BASE)]);
            value /= BASE;
        }
        return encoded.reverse().toString();
    }
}
