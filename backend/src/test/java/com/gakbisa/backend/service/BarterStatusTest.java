package com.gakbisa.backend.service;

import com.gakbisa.backend.domain.BarterStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Skenario uji §8.2 no.6: transisi status barter hanya yang valid. */
class BarterStatusTest {

    @Test
    void pendingDapatDiterimaDitolakAtauDibatalkan() {
        assertTrue(BarterStatus.PENDING.canTransitionTo(BarterStatus.ACCEPTED));
        assertTrue(BarterStatus.PENDING.canTransitionTo(BarterStatus.REJECTED));
        assertTrue(BarterStatus.PENDING.canTransitionTo(BarterStatus.CANCELLED));
    }

    @Test
    void statusFinalTidakDapatBerubah() {
        for (BarterStatus terminal : new BarterStatus[]{BarterStatus.REJECTED, BarterStatus.CANCELLED, BarterStatus.COMPLETED}) {
            for (BarterStatus next : BarterStatus.values()) {
                assertFalse(terminal.canTransitionTo(next), terminal + " -> " + next + " harus ditolak");
            }
        }
    }

    @Test
    void acceptedDapatSelesaiAtauBatal() {
        assertTrue(BarterStatus.ACCEPTED.canTransitionTo(BarterStatus.COMPLETED));
        assertTrue(BarterStatus.ACCEPTED.canTransitionTo(BarterStatus.CANCELLED));
        assertFalse(BarterStatus.ACCEPTED.canTransitionTo(BarterStatus.PENDING));
    }
}
