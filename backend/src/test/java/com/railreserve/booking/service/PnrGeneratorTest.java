package com.railreserve.booking.service;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PnrGeneratorTest {

    private final PnrGenerator generator = new PnrGenerator();

    @Test
    void generatesTenCharacterPnrsFromTheUnambiguousAlphabet() {
        for (int i = 0; i < 1000; i++) {
            String pnr = generator.generate();
            assertThat(pnr).hasSize(10).matches("[2-9A-HJ-NP-Z]+");
            // no ambiguous characters
            assertThat(pnr).doesNotContain("0").doesNotContain("O")
                    .doesNotContain("1").doesNotContain("I");
        }
    }

    @Test
    void producesNoCollisionsAcrossManyGenerations() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            seen.add(generator.generate());
        }
        // 32^10 space -> collisions in 10k draws are astronomically unlikely
        assertThat(seen).hasSize(10_000);
    }
}
