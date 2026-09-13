package com.fairhome.dedup;

import com.fairhome.support.NamedEnumConverters;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * A suspected duplicate pair, always between the newer application and the earlier one it resembles.
 *
 * <p>Flags are records of a machine's suspicion, not a decision. Nothing is removed from the draw
 * until a human resolves the flag, and the resolution keeps who decided, when, and why.
 */
@Entity
@Table(name = "duplicate_flags", indexes = {
        @Index(name = "ix_dup_status", columnList = "resolution"),
        @Index(name = "ix_dup_new", columnList = "newApplicationId"),
        @Index(name = "ix_dup_existing", columnList = "existingApplicationId")
})
public class DuplicateFlag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The application that was being submitted when the match was found. */
    @Column(nullable = false)
    private Long newApplicationId;

    @Column(nullable = false, length = 24)
    private String newApplicationNumber;

    /** The already-recorded application it matched. */
    @Column(nullable = false)
    private Long existingApplicationId;

    @Column(nullable = false, length = 24)
    private String existingApplicationNumber;

    @Convert(converter = NamedEnumConverters.MatchTypeConverter.class)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 32)
    private MatchType matchType;

    /** 0..1 confidence. Exact national id matches are 1.0. */
    @Column(nullable = false)
    private Double score;

    @Column(nullable = false, length = 600)
    private String evidence;

    @Convert(converter = NamedEnumConverters.DuplicateResolutionConverter.class)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 32)
    private DuplicateResolution resolution = DuplicateResolution.OPEN;

    @Column(nullable = false)
    private Instant detectedAt;

    private Instant resolvedAt;

    @Column(length = 120)
    private String resolvedBy;

    @Column(length = 600)
    private String resolutionNote;

    /**
     * The surviving application when a duplicate was confirmed. Null when the officer decided the pair
     * are different people, because in that case nothing was dropped and there is nothing to name.
     */
    @Column(length = 24)
    private String keptApplicationNumber;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getNewApplicationId() {
        return newApplicationId;
    }

    public void setNewApplicationId(Long newApplicationId) {
        this.newApplicationId = newApplicationId;
    }

    public String getNewApplicationNumber() {
        return newApplicationNumber;
    }

    public void setNewApplicationNumber(String newApplicationNumber) {
        this.newApplicationNumber = newApplicationNumber;
    }

    public Long getExistingApplicationId() {
        return existingApplicationId;
    }

    public void setExistingApplicationId(Long existingApplicationId) {
        this.existingApplicationId = existingApplicationId;
    }

    public String getExistingApplicationNumber() {
        return existingApplicationNumber;
    }

    public void setExistingApplicationNumber(String existingApplicationNumber) {
        this.existingApplicationNumber = existingApplicationNumber;
    }

    public MatchType getMatchType() {
        return matchType;
    }

    public void setMatchType(MatchType matchType) {
        this.matchType = matchType;
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }

    public String getEvidence() {
        return evidence;
    }

    public void setEvidence(String evidence) {
        this.evidence = evidence;
    }

    public DuplicateResolution getResolution() {
        return resolution;
    }

    public void setResolution(DuplicateResolution resolution) {
        this.resolution = resolution;
    }

    public Instant getDetectedAt() {
        return detectedAt;
    }

    public void setDetectedAt(Instant detectedAt) {
        this.detectedAt = detectedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public String getResolvedBy() {
        return resolvedBy;
    }

    public void setResolvedBy(String resolvedBy) {
        this.resolvedBy = resolvedBy;
    }

    public String getResolutionNote() {
        return resolutionNote;
    }

    public void setResolutionNote(String resolutionNote) {
        this.resolutionNote = resolutionNote;
    }

    public String getKeptApplicationNumber() {
        return keptApplicationNumber;
    }

    public void setKeptApplicationNumber(String keptApplicationNumber) {
        this.keptApplicationNumber = keptApplicationNumber;
    }

    public int getScorePercent() {
        return score == null ? 0 : (int) Math.round(score * 100);
    }

    /** What to show in a list where a single line has to describe the outcome. */
    public String getOutcomeDescription() {
        return switch (resolution) {
            case OPEN -> "Awaiting an officer's decision";
            case CONFIRMED_DUPLICATE -> keptApplicationNumber + " kept, the other rejected";
            case NOT_A_DUPLICATE -> "Different people, both kept in the draw";
        };
    }

    public String getDetectedAtDisplay() {
        return com.fairhome.support.Display.dateTime(detectedAt);
    }

    public String getResolvedAtDisplay() {
        return com.fairhome.support.Display.dateTime(resolvedAt);
    }
}
