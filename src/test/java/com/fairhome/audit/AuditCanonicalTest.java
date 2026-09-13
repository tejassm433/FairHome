package com.fairhome.audit;

import com.fairhome.support.Hashes;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class AuditCanonicalTest {

    @Test
    void hashIsStableWhenInstantNanosAreDroppedOnReload() {
        Instant written = Instant.parse("2026-09-12T10:00:00.123456789Z");
        Instant reloaded = written.truncatedTo(ChronoUnit.MILLIS);

        AuditEvent original = event(written);
        AuditEvent afterLoad = event(reloaded);

        assertEquals(original.canonicalPayload(), afterLoad.canonicalPayload());
        assertEquals(Hashes.sha256Hex(original.canonicalPayload()),
                Hashes.sha256Hex(afterLoad.canonicalPayload()));
    }

    @Test
    void changingDetailBreaksTheHash() {
        AuditEvent a = event(Instant.parse("2026-09-12T10:00:00Z"));
        AuditEvent b = event(Instant.parse("2026-09-12T10:00:00Z"));
        b.setDetail("tampered");
        assertNotEquals(Hashes.sha256Hex(a.canonicalPayload()),
                Hashes.sha256Hex(b.canonicalPayload()));
    }

    private static AuditEvent event(Instant when) {
        AuditEvent event = new AuditEvent();
        event.setSequence(1L);
        event.setOccurredAt(when);
        event.setAction("APPLICATION_SUBMITTED_ONLINE");
        event.setSubject("FH-2026-000001");
        event.setActor("system");
        event.setDetail("accepted");
        event.setPreviousHash("0".repeat(64));
        return event;
    }
}
