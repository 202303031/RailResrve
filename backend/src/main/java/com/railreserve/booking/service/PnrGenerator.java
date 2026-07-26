package com.railreserve.booking.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Generates a 10-character PNR from an unambiguous alphabet (no 0/O/1/I). Uniqueness is
 * ultimately guaranteed by the {@code uq_booking_pnr} constraint; the booking service
 * regenerates on the astronomically rare collision.
 */
@Component
public class PnrGenerator {

    private static final char[] ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();
    private static final int LENGTH = 10;

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }
}
