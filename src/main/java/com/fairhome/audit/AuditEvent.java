package com.fairhome.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Append-only audit entry, chained by hash.
 *
 * <p>Each entry hashes its own contents together with the previous entry's hash. Deleting or editing
 * any entry breaks the chain from that point onward, which is verifiable from the admin audit page.
 * That is much weaker than a real append-only store, but it makes quiet tampering detectable, which
 * is the property that matters when the log is evidence.
 */
@Entity
@Table(name = "audit_events", indexes = {
        @Index(name = "ix_audit_seq", columnList = "sequence", unique = true),
        @Index(name = "ix_audit_subject", columnList = "subject")
})
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long sequence;

    @Column(nullable = false)
    private Instant occurredAt;

    @Column(nullable = false, length = 60)
    private String action;

    /** What the action was about: an application number, a draw run, a rule version. */
    @Column(nullable = false, length = 120)
    private String subject;

    @Column(nullable = false, length = 120)
    private String actor;

    @Column(nullable = false, length = 2000)
    private String detail;

    @Column(nullable = false, length = 64)
    private String previousHash;

    @Column(nullable = false, length = 64)
    private String entryHash;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSequence() {
        return sequence;
    }

    public void setSequence(Long sequence) {
        this.sequence = sequence;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getActor() {
        return actor;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public String getPreviousHash() {
        return previousHash;
    }

    public void setPreviousHash(String previousHash) {
        this.previousHash = previousHash;
    }

    public String getEntryHash() {
        return entryHash;
    }

    public void setEntryHash(String entryHash) {
        this.entryHash = entryHash;
    }

    public String getShortHash() {
        return entryHash == null ? "" : entryHash.substring(0, 12);
    }

    public String getOccurredAtDisplay() {
        return com.fairhome.support.Display.timestamp(occurredAt);
    }

    /**
     * The exact string that is hashed. Instant is stored as epoch millis so a reload from H2
     * cannot change the payload the way {@code Instant.toString()} (nanos vs millis) would.
     */
    public String canonicalPayload() {
        long when = occurredAt == null ? 0L : occurredAt.toEpochMilli();
        return sequence + "|" + when + "|" + action + "|" + subject + "|" + actor + "|"
                + detail + "|" + previousHash;
    }
}
