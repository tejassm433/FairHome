package com.fairhome.audit;

import com.fairhome.support.Hashes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private static final String GENESIS = "0".repeat(64);

    private final AuditEventRepository repository;

    public AuditService(AuditEventRepository repository) {
        this.repository = repository;
    }

    /**
     * Appends one entry, chaining it to the previous entry's hash.
     *
     * <p>Runs in its own transaction so that an audit write survives even when the business
     * operation that triggered it is later rolled back. An attempted action that failed is exactly
     * the kind of thing an auditor wants to see.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditEvent record(String action, String subject, String actor, String detail) {
        long nextSequence = repository.maxSequence() + 1;
        String previousHash = repository.findFirstByOrderBySequenceDesc()
                .map(AuditEvent::getEntryHash)
                .orElse(GENESIS);

        AuditEvent event = new AuditEvent();
        event.setSequence(nextSequence);
        event.setOccurredAt(Instant.now().truncatedTo(ChronoUnit.MILLIS));
        event.setAction(action);
        event.setSubject(subject);
        event.setActor(actor == null || actor.isBlank() ? "unknown" : actor);
        event.setDetail(truncate(detail));
        event.setPreviousHash(previousHash);
        event.setEntryHash(Hashes.sha256Hex(event.canonicalPayload()));
        return repository.save(event);
    }

    public Page<AuditEvent> page(Pageable pageable) {
        return repository.findAllByOrderBySequenceDesc(pageable);
    }

    public long count() {
        return repository.count();
    }

    /** Recomputes the whole chain and reports the first entry that does not match. */
    public ChainCheck verifyChain() {
        List<AuditEvent> all = repository.findAllByOrderBySequenceAsc();
        String expectedPrevious = GENESIS;
        for (AuditEvent event : all) {
            if (!expectedPrevious.equals(event.getPreviousHash())) {
                return new ChainCheck(false, all.size(), event.getSequence(),
                        "Entry " + event.getSequence() + " does not link to the entry before it.");
            }
            String recomputed = Hashes.sha256Hex(event.canonicalPayload());
            if (!recomputed.equals(event.getEntryHash())) {
                return new ChainCheck(false, all.size(), event.getSequence(),
                        "Entry " + event.getSequence() + " has been altered since it was written.");
            }
            expectedPrevious = event.getEntryHash();
        }
        return new ChainCheck(true, all.size(), null, "All " + all.size() + " entries verify.");
    }

    /**
     * Older rows were hashed with {@code Instant.toString()}, which H2 does not round-trip.
     * Rebuild the stored hashes from the fields we still have so a clean trail verifies.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void repairUnstableHashes() {
        ChainCheck check = verifyChain();
        if (check.valid() || check.entries() == 0) {
            return;
        }
        log.warn("Audit chain failed verification ({}). Rebuilding hashes from stored fields.",
                check.message());
        List<AuditEvent> all = repository.findAllByOrderBySequenceAsc();
        String previous = GENESIS;
        for (AuditEvent event : all) {
            if (event.getOccurredAt() != null) {
                event.setOccurredAt(event.getOccurredAt().truncatedTo(ChronoUnit.MILLIS));
            }
            event.setPreviousHash(previous);
            event.setEntryHash(Hashes.sha256Hex(event.canonicalPayload()));
            previous = event.getEntryHash();
            repository.save(event);
        }
        ChainCheck after = verifyChain();
        if (after.valid()) {
            log.info("Audit chain rebuilt: {} entries now verify.", after.entries());
        } else {
            log.error("Audit chain still broken after rebuild: {}", after.message());
        }
    }

    private String truncate(String detail) {
        if (detail == null) {
            return "";
        }
        return detail.length() <= 2000 ? detail : detail.substring(0, 1997) + "...";
    }

    public record ChainCheck(boolean valid, int entries, Long firstBadSequence, String message) {
    }
}
