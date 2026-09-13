package com.fairhome.draw;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

/**
 * The decision recorded for one application in one draw run, with the reasoning attached.
 *
 * <p>Every application gets a row, including the ones that were not selected. The point of this
 * table is that "why am I number 812 and not number 600?" has a stored, printable answer rather
 * than being re-derived by whoever is asked.
 */
@Entity
@Table(name = "allocations", indexes = {
        @Index(name = "ix_alloc_run", columnList = "drawRunId"),
        @Index(name = "ix_alloc_app", columnList = "applicationId"),
        @Index(name = "ix_alloc_run_outcome", columnList = "drawRunId,outcome")
})
public class Allocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long drawRunId;

    @Column(nullable = false)
    private Long applicationId;

    @Column(nullable = false, length = 24)
    private String applicationNumber;

    @Column(nullable = false, length = 160)
    private String applicantName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private Outcome outcome;

    /** Income category resolved from declared income under the run's rule version. */
    @Column(length = 16)
    private String categoryCode;

    /** Reservation code the seat came from, or OPEN for the unreserved part of the category. */
    @Column(length = 24)
    private String poolCode;

    /** Rank within the pool the seat was drawn from, 1-based. */
    private Integer rankInPool;

    /** Rank within the applicant's income category across all its applications, 1-based. */
    private Integer rankInCategory;

    private Integer categoryApplicantCount;

    /** Allotment serial within the run, assigned in category then pool order. */
    private Integer seatNumber;

    private Integer waitlistPosition;

    @Column(nullable = false, length = 64)
    private String lotteryToken;

    @Column(nullable = false, length = 48)
    private String reasonCode;

    @Column(nullable = false, length = 1000)
    private String reasonText;

    /** Ordered narrative of the checks and comparisons that produced this outcome. */
    @Lob
    @Column(nullable = false)
    private String explanation;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getDrawRunId() {
        return drawRunId;
    }

    public void setDrawRunId(Long drawRunId) {
        this.drawRunId = drawRunId;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(Long applicationId) {
        this.applicationId = applicationId;
    }

    public String getApplicationNumber() {
        return applicationNumber;
    }

    public void setApplicationNumber(String applicationNumber) {
        this.applicationNumber = applicationNumber;
    }

    public String getApplicantName() {
        return applicantName;
    }

    public void setApplicantName(String applicantName) {
        this.applicantName = applicantName;
    }

    public Outcome getOutcome() {
        return outcome;
    }

    public void setOutcome(Outcome outcome) {
        this.outcome = outcome;
    }

    public String getCategoryCode() {
        return categoryCode;
    }

    public void setCategoryCode(String categoryCode) {
        this.categoryCode = categoryCode;
    }

    public String getPoolCode() {
        return poolCode;
    }

    public void setPoolCode(String poolCode) {
        this.poolCode = poolCode;
    }

    public Integer getRankInPool() {
        return rankInPool;
    }

    public void setRankInPool(Integer rankInPool) {
        this.rankInPool = rankInPool;
    }

    public Integer getRankInCategory() {
        return rankInCategory;
    }

    public void setRankInCategory(Integer rankInCategory) {
        this.rankInCategory = rankInCategory;
    }

    public Integer getCategoryApplicantCount() {
        return categoryApplicantCount;
    }

    public void setCategoryApplicantCount(Integer categoryApplicantCount) {
        this.categoryApplicantCount = categoryApplicantCount;
    }

    public Integer getSeatNumber() {
        return seatNumber;
    }

    public void setSeatNumber(Integer seatNumber) {
        this.seatNumber = seatNumber;
    }

    public Integer getWaitlistPosition() {
        return waitlistPosition;
    }

    public void setWaitlistPosition(Integer waitlistPosition) {
        this.waitlistPosition = waitlistPosition;
    }

    public String getLotteryToken() {
        return lotteryToken;
    }

    public void setLotteryToken(String lotteryToken) {
        this.lotteryToken = lotteryToken;
    }

    public String getShortToken() {
        return lotteryToken == null ? "" : lotteryToken.substring(0, 16);
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public void setReasonCode(String reasonCode) {
        this.reasonCode = reasonCode;
    }

    public String getReasonText() {
        return reasonText;
    }

    public void setReasonText(String reasonText) {
        this.reasonText = reasonText;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public String[] getExplanationLines() {
        return explanation == null ? new String[0] : explanation.split("\n");
    }
}
