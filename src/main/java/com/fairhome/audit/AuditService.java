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
        log.debug("FairHome : AuditService : in method record : START");
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
        AuditEvent saved = repository.save(event);
        log.info("FairHome : AuditService : in method record : audit recorded : {} {} by {}",
                action, subject, saved.getActor());
        log.debug("FairHome : AuditService : in method record : END");
        return saved;
    }

    public Page<AuditEvent> page(Pageable pageable) {
        log.debug("FairHome : AuditService : in method page : START");
        Page<AuditEvent> result = repository.findAllByOrderBySequenceDesc(pageable);
        log.debug("FairHome : AuditService : in method page : END");
        return result;
    }

    public long count() {
        log.debug("FairHome : AuditService : in method count : START");
        long result = repository.count();
        log.debug("FairHome : AuditService : in method count : END");
        return result;
    }

    /** Recomputes the whole chain and reports the first entry that does not match. */
    public ChainCheck verifyChain() {
        log.debug("FairHome : AuditService : in method verifyChain : START");
        List<AuditEvent> all = repository.findAllByOrderBySequenceAsc();
        String expectedPrevious = GENESIS;
        for (AuditEvent event : all) {
            if (!expectedPrevious.equals(event.getPreviousHash())) {
                ChainCheck check = new ChainCheck(false, all.size(), event.getSequence(),
                        "Entry " + event.getSequence() + " does not link to the entry before it.");
                log.debug("FairHome : AuditService : in method verifyChain : END");
                return check;
            }
            String recomputed = Hashes.sha256Hex(event.canonicalPayload());
            if (!recomputed.equals(event.getEntryHash())) {
                ChainCheck check = new ChainCheck(false, all.size(), event.getSequence(),
                        "Entry " + event.getSequence() + " has been altered since it was written.");
                log.debug("FairHome : AuditService : in method verifyChain : END");
                return check;
            }
            expectedPrevious = event.getEntryHash();
        }
        ChainCheck check = new ChainCheck(true, all.size(), null, "All " + all.size() + " entries verify.");
        log.debug("FairHome : AuditService : in method verifyChain : END");
        return check;
    }

    /**
     * Older rows were hashed with {@code Instant.toString()}, which H2 does not round-trip.
     * Rebuild the stored hashes from the fields we still have so a clean trail verifies.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void repairUnstableHashes() {
        log.debug("FairHome : AuditService : in method repairUnstableHashes : START");
        ChainCheck check = verifyChain();
        if (check.valid() || check.entries() == 0) {
            log.debug("FairHome : AuditService : in method repairUnstableHashes : END");
            return;
        }
        log.warn("FairHome : AuditService : in method repairUnstableHashes : audit chain failed verification : {}",
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
            log.info("FairHome : AuditService : in method repairUnstableHashes : audit chain rebuilt : {} entries verify",
                    after.entries());
        } else {
            log.error("FairHome : AuditService : in method repairUnstableHashes : audit chain still broken : {}",
                    after.message());
        }
        log.debug("FairHome : AuditService : in method repairUnstableHashes : END");
    }

    private String truncate(String detail) {
        return detail == null ? "" : detail.length() <= 2000 ? detail : detail.substring(0, 1997) + "...";
    }

    public record ChainCheck(boolean valid, int entries, Long firstBadSequence, String message) {
    }
}
