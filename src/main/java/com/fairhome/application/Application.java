package com.fairhome.application;

import com.fairhome.rules.Gender;
import com.fairhome.support.Display;
import com.fairhome.support.NationalId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;

/**
 * One application, whether it arrived through the public form or was typed in from paper.
 *
 * <p>Applicant details are stored on the application itself rather than on a shared person record.
 * An application is the immutable claim someone made on a given date; if the same person applies
 * twice with slightly different details, we want both claims preserved exactly as submitted so the
 * duplicate decision can be reviewed and defended later. The "person" is derived from the
 * canonical national id, not asserted by a foreign key.
 */
@Entity
@Table(name = "applications", indexes = {
        @Index(name = "ix_app_number", columnList = "applicationNumber", unique = true),
        @Index(name = "ix_app_national_id", columnList = "nationalId"),
        @Index(name = "ix_app_name_dob", columnList = "normalisedName,dateOfBirth"),
        @Index(name = "ix_app_dob", columnList = "dateOfBirth"),
        @Index(name = "ix_app_phone", columnList = "normalisedPhone"),
        @Index(name = "ix_app_email", columnList = "normalisedEmail"),
        @Index(name = "ix_app_status", columnList = "status")
})
public class Application {

    /**
     * A sequence rather than an identity column: Hibernate hands us the id at persist time, which
     * lets the human-facing application number be derived from it inside the same transaction and
     * still be written by a single insert.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "application_seq")
    @SequenceGenerator(name = "application_seq", sequenceName = "application_seq", allocationSize = 1)
    private Long id;

    @Column(nullable = false, unique = true, length = 24)
    private String applicationNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Channel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ApplicationStatus status = ApplicationStatus.SUBMITTED;

    /** When the applicant actually applied. For paper forms this is the date on the form. */
    @Column(nullable = false)
    private Instant submittedAt;

    /** When the row entered this system. Differs from submittedAt for back-dated paper entries. */
    @Column(nullable = false)
    private Instant recordedAt;

    /** Which admin keyed in an offline application, for accountability. */
    @Column(length = 120)
    private String recordedBy;

    /** Physical form / counter receipt number, so a paper trail can be traced back. */
    @Column(length = 60)
    private String paperReference;

    @Column(nullable = false, length = 160)
    private String fullName;

    @Column(nullable = false, length = 160)
    private String normalisedName;

    @Column(nullable = false)
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Gender gender;

    @Column(nullable = false, length = 12)
    private String nationalId;

    @Column(nullable = false, length = 4)
    private String nationalIdLast4;

    @Column(length = 20)
    private String phone;

    @Column(length = 20)
    private String normalisedPhone;

    @Column(length = 160)
    private String email;

    @Column(length = 160)
    private String normalisedEmail;

    @Column(length = 400)
    private String addressLine;

    @Column(length = 120)
    private String cityOrWard;

    @Column(length = 12)
    private String postalCode;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal annualIncome;

    /** Continuous years living in the scheme area; drives the local-resident reserved quota. */
    @Column(nullable = false)
    private Integer yearsInArea = 0;

    @Column(nullable = false)
    private Boolean differentlyAbled = false;

    @Column(nullable = false)
    private Boolean exServiceman = false;

    @Column(nullable = false)
    private Boolean firstTimeHomeBuyer = true;

    /**
     * Secret only the applicant knows, used together with the application number for the public
     * status lookup so one applicant cannot read another's placement.
     */
    @Column(nullable = false, length = 64)
    private String statusLookupKey;

    @Column(length = 500)
    private String statusNote;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getApplicationNumber() {
        return applicationNumber;
    }

    public void setApplicationNumber(String applicationNumber) {
        this.applicationNumber = applicationNumber;
    }

    public Channel getChannel() {
        return channel;
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
    }

    public ApplicationStatus getStatus() {
        return status;
    }

    public void setStatus(ApplicationStatus status) {
        this.status = status;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(Instant submittedAt) {
        this.submittedAt = submittedAt;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(Instant recordedAt) {
        this.recordedAt = recordedAt;
    }

    public String getRecordedBy() {
        return recordedBy;
    }

    public void setRecordedBy(String recordedBy) {
        this.recordedBy = recordedBy;
    }

    public String getPaperReference() {
        return paperReference;
    }

    public void setPaperReference(String paperReference) {
        this.paperReference = paperReference;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getNormalisedName() {
        return normalisedName;
    }

    public void setNormalisedName(String normalisedName) {
        this.normalisedName = normalisedName;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public Gender getGender() {
        return gender;
    }

    public void setGender(Gender gender) {
        this.gender = gender;
    }

    public String getNationalId() {
        return nationalId;
    }

    public void setNationalId(String nationalId) {
        this.nationalId = nationalId;
        this.nationalIdLast4 = NationalId.last4(nationalId);
    }

    public String getNationalIdLast4() {
        return nationalIdLast4;
    }

    public void setNationalIdLast4(String nationalIdLast4) {
        this.nationalIdLast4 = nationalIdLast4;
    }

    public String getMaskedNationalId() {
        return NationalId.masked(nationalId);
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getNormalisedPhone() {
        return normalisedPhone;
    }

    public void setNormalisedPhone(String normalisedPhone) {
        this.normalisedPhone = normalisedPhone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getNormalisedEmail() {
        return normalisedEmail;
    }

    public void setNormalisedEmail(String normalisedEmail) {
        this.normalisedEmail = normalisedEmail;
    }

    public String getAddressLine() {
        return addressLine;
    }

    public void setAddressLine(String addressLine) {
        this.addressLine = addressLine;
    }

    public String getCityOrWard() {
        return cityOrWard;
    }

    public void setCityOrWard(String cityOrWard) {
        this.cityOrWard = cityOrWard;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
    }

    public BigDecimal getAnnualIncome() {
        return annualIncome;
    }

    public void setAnnualIncome(BigDecimal annualIncome) {
        this.annualIncome = annualIncome;
    }

    public Integer getYearsInArea() {
        return yearsInArea;
    }

    public void setYearsInArea(Integer yearsInArea) {
        this.yearsInArea = yearsInArea;
    }

    public Boolean getDifferentlyAbled() {
        return differentlyAbled;
    }

    public void setDifferentlyAbled(Boolean differentlyAbled) {
        this.differentlyAbled = differentlyAbled;
    }

    public Boolean getExServiceman() {
        return exServiceman;
    }

    public void setExServiceman(Boolean exServiceman) {
        this.exServiceman = exServiceman;
    }

    public Boolean getFirstTimeHomeBuyer() {
        return firstTimeHomeBuyer;
    }

    public void setFirstTimeHomeBuyer(Boolean firstTimeHomeBuyer) {
        this.firstTimeHomeBuyer = firstTimeHomeBuyer;
    }

    public String getStatusLookupKey() {
        return statusLookupKey;
    }

    public void setStatusLookupKey(String statusLookupKey) {
        this.statusLookupKey = statusLookupKey;
    }

    public String getStatusNote() {
        return statusNote;
    }

    public void setStatusNote(String statusNote) {
        this.statusNote = statusNote;
    }

    public int ageOn(LocalDate on) {
        return dateOfBirth == null ? -1 : Period.between(dateOfBirth, on).getYears();
    }

    public int getAge() {
        return ageOn(LocalDate.now());
    }

    public String getSubmittedOnDisplay() {
        return Display.date(submittedAt);
    }

    public String getRecordedAtDisplay() {
        return Display.dateTime(recordedAt);
    }

    public String getDateOfBirthDisplay() {
        return Display.date(dateOfBirth);
    }
}
