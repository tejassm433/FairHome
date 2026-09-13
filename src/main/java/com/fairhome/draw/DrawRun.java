package com.fairhome.draw;

import com.fairhome.support.NamedEnumConverters;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * One execution of the allocation engine.
 *
 * <p>Dry runs exist so officers can rehearse and compare; exactly one run may ever be published as
 * the official result. Each run pins the rule version, the rule content hash and the seed it used,
 * and stores a hash over its own ordered results, which together make the outcome reproducible and
 * tamper-evident.
 */
@Entity
@Table(name = "draw_runs")
public class DrawRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Convert(converter = NamedEnumConverters.DrawModeConverter.class)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 16)
    private DrawMode mode;

    @Column(nullable = false)
    private Instant executedAt;

    @Column(length = 120)
    private String executedBy;

    @Column(nullable = false)
    private Integer ruleSetVersion;

    @Column(nullable = false, length = 64)
    private String ruleSetHash;

    @Column(nullable = false, length = 200)
    private String seed;

    @Column(nullable = false)
    private Integer totalFlats;

    @Column(nullable = false)
    private Integer applicationsConsidered;

    @Column(nullable = false)
    private Integer allotted;

    @Column(nullable = false)
    private Integer waitlisted;

    @Column(nullable = false)
    private Integer notSelected;

    @Column(nullable = false)
    private Integer excluded;

    @Column(nullable = false)
    private Integer vacantSeats;

    /** SHA-256 over the ordered result lines; lets anyone prove the published list is unaltered. */
    @Column(nullable = false, length = 64)
    private String resultsHash;

    @Column(nullable = false)
    private boolean published;

    private Instant publishedAt;

    /** Human-readable, step-by-step record of how seats were divided, kept for publication. */
    @Lob
    @Column(nullable = false)
    private String quotaWorkings;

    @Column(length = 500)
    private String note;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public DrawMode getMode() {
        return mode;
    }

    public void setMode(DrawMode mode) {
        this.mode = mode;
    }

    public Instant getExecutedAt() {
        return executedAt;
    }

    public void setExecutedAt(Instant executedAt) {
        this.executedAt = executedAt;
    }

    public String getExecutedBy() {
        return executedBy;
    }

    public void setExecutedBy(String executedBy) {
        this.executedBy = executedBy;
    }

    public Integer getRuleSetVersion() {
        return ruleSetVersion;
    }

    public void setRuleSetVersion(Integer ruleSetVersion) {
        this.ruleSetVersion = ruleSetVersion;
    }

    public String getRuleSetHash() {
        return ruleSetHash;
    }

    public void setRuleSetHash(String ruleSetHash) {
        this.ruleSetHash = ruleSetHash;
    }

    public String getSeed() {
        return seed;
    }

    public void setSeed(String seed) {
        this.seed = seed;
    }

    public Integer getTotalFlats() {
        return totalFlats;
    }

    public void setTotalFlats(Integer totalFlats) {
        this.totalFlats = totalFlats;
    }

    public Integer getApplicationsConsidered() {
        return applicationsConsidered;
    }

    public void setApplicationsConsidered(Integer applicationsConsidered) {
        this.applicationsConsidered = applicationsConsidered;
    }

    public Integer getAllotted() {
        return allotted;
    }

    public void setAllotted(Integer allotted) {
        this.allotted = allotted;
    }

    public Integer getWaitlisted() {
        return waitlisted;
    }

    public void setWaitlisted(Integer waitlisted) {
        this.waitlisted = waitlisted;
    }

    public Integer getNotSelected() {
        return notSelected;
    }

    public void setNotSelected(Integer notSelected) {
        this.notSelected = notSelected;
    }

    public Integer getExcluded() {
        return excluded;
    }

    public void setExcluded(Integer excluded) {
        this.excluded = excluded;
    }

    public Integer getVacantSeats() {
        return vacantSeats;
    }

    public void setVacantSeats(Integer vacantSeats) {
        this.vacantSeats = vacantSeats;
    }

    public String getResultsHash() {
        return resultsHash;
    }

    public void setResultsHash(String resultsHash) {
        this.resultsHash = resultsHash;
    }

    public String getShortResultsHash() {
        return resultsHash == null ? "" : resultsHash.substring(0, 12);
    }

    public boolean isPublished() {
        return published;
    }

    public void setPublished(boolean published) {
        this.published = published;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public String getQuotaWorkings() {
        return quotaWorkings;
    }

    public void setQuotaWorkings(String quotaWorkings) {
        this.quotaWorkings = quotaWorkings;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getExecutedAtDisplay() {
        return com.fairhome.support.Display.timestamp(executedAt);
    }

    public String getPublishedAtDisplay() {
        return com.fairhome.support.Display.timestamp(publishedAt);
    }

    public String getShortRuleSetHash() {
        return ruleSetHash == null ? "" : ruleSetHash.substring(0, 12);
    }

    public String[] getQuotaWorkingsLines() {
        return quotaWorkings == null ? new String[0] : quotaWorkings.split("\n");
    }
}
