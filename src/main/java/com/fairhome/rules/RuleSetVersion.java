package com.fairhome.rules;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * An immutable, published version of the rule book.
 *
 * <p>Rule versions are never edited in place. Publishing a change writes a new row with a new
 * version number and content hash, so every completed draw can name the exact rules it ran under
 * and nobody can retro-fit the policy to the result.
 */
@Entity
@Table(name = "rule_set_versions")
public class RuleSetVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Integer version;

    @Column(nullable = false, length = 120)
    private String versionLabel;

    @Lob
    @Column(nullable = false)
    private String json;

    /** SHA-256 of the canonical JSON. Quotable in a press note or an affidavit. */
    @Column(nullable = false, length = 64)
    private String contentHash;

    @Column(nullable = false)
    private Instant publishedAt;

    @Column(length = 120)
    private String publishedBy;

    @Column(length = 500)
    private String changeNote;

    @Column(nullable = false)
    private boolean active;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getVersionLabel() {
        return versionLabel;
    }

    public void setVersionLabel(String versionLabel) {
        this.versionLabel = versionLabel;
    }

    public String getJson() {
        return json;
    }

    public void setJson(String json) {
        this.json = json;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public String getShortHash() {
        return contentHash == null ? "" : contentHash.substring(0, 12);
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public String getPublishedBy() {
        return publishedBy;
    }

    public void setPublishedBy(String publishedBy) {
        this.publishedBy = publishedBy;
    }

    public String getChangeNote() {
        return changeNote;
    }

    public void setChangeNote(String changeNote) {
        this.changeNote = changeNote;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getPublishedAtDisplay() {
        return com.fairhome.support.Display.timestamp(publishedAt);
    }
}
