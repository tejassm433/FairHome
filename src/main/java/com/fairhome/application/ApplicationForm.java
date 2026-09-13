package com.fairhome.application;

import com.fairhome.rules.Gender;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * What an applicant fills in, on either channel.
 *
 * <p>The offline fields at the bottom are only ever populated by the admin entry screen; the public
 * form has no way to set them, so a member of the public cannot back-date their own application.
 */
public class ApplicationForm {

    @NotBlank(message = "Please enter the applicant's full name")
    @Size(max = 160, message = "Name cannot be longer than 160 characters")
    private String fullName;

    @NotNull(message = "Please enter the date of birth")
    private LocalDate dateOfBirth;

    @NotNull(message = "Please select a gender")
    private Gender gender;

    @NotBlank(message = "Please enter the 12 digit national ID number")
    private String nationalId;

    @Pattern(regexp = "^$|^[0-9+\\- ()]{7,20}$", message = "Please enter a valid phone number")
    private String phone;

    @Email(message = "Please enter a valid email address")
    @Size(max = 160)
    private String email;

    @NotBlank(message = "Please enter the address")
    @Size(max = 400)
    private String addressLine;

    @NotBlank(message = "Please enter the city or ward")
    @Size(max = 120)
    private String cityOrWard;

    @Pattern(regexp = "^$|^[0-9]{6}$", message = "Postal code should be 6 digits")
    private String postalCode;

    @NotNull(message = "Please enter the annual household income")
    @PositiveOrZero(message = "Income cannot be negative")
    private BigDecimal annualIncome;

    @NotNull(message = "Please enter how many years the applicant has lived in the scheme area")
    @Min(value = 0, message = "Years in the area cannot be negative")
    @Max(value = 120, message = "Please check the years in the area")
    private Integer yearsInArea = 0;

    private boolean differentlyAbled;

    private boolean exServiceman;

    private boolean firstTimeHomeBuyer = true;

    private boolean declarationAccepted;

    // Offline entry only.
    @Size(max = 120)
    private String recordedBy;

    @Size(max = 60)
    private String paperReference;

    /** The date printed on the paper form, which is what counts as the application date. */
    private LocalDate paperSubmittedOn;

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
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
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
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

    public boolean isDifferentlyAbled() {
        return differentlyAbled;
    }

    public void setDifferentlyAbled(boolean differentlyAbled) {
        this.differentlyAbled = differentlyAbled;
    }

    public boolean isExServiceman() {
        return exServiceman;
    }

    public void setExServiceman(boolean exServiceman) {
        this.exServiceman = exServiceman;
    }

    public boolean isFirstTimeHomeBuyer() {
        return firstTimeHomeBuyer;
    }

    public void setFirstTimeHomeBuyer(boolean firstTimeHomeBuyer) {
        this.firstTimeHomeBuyer = firstTimeHomeBuyer;
    }

    public boolean isDeclarationAccepted() {
        return declarationAccepted;
    }

    public void setDeclarationAccepted(boolean declarationAccepted) {
        this.declarationAccepted = declarationAccepted;
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

    public LocalDate getPaperSubmittedOn() {
        return paperSubmittedOn;
    }

    public void setPaperSubmittedOn(LocalDate paperSubmittedOn) {
        this.paperSubmittedOn = paperSubmittedOn;
    }
}
