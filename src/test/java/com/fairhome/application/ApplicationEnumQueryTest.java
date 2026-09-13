package com.fairhome.application;

import com.fairhome.rules.Gender;
import com.fairhome.support.NameMatching;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:enumquery;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "fairhome.demo-data.enabled=false"
})
@Transactional
class ApplicationEnumQueryTest {

    @Autowired
    private ApplicationRepository applications;

    @Test
    void countsByChannelAndStatusWithoutTypeConversionError() {
        applications.save(sample("FH-ON-1", Channel.ONLINE, ApplicationStatus.SUBMITTED));
        applications.save(sample("FH-OFF-1", Channel.OFFLINE, ApplicationStatus.WITHDRAWN));

        assertEquals(1, applications.countByChannel(Channel.ONLINE));
        assertEquals(1, applications.countByChannel(Channel.OFFLINE));
        assertEquals(1, applications.countByStatus(ApplicationStatus.SUBMITTED));
        assertEquals(1, applications.countByStatus(ApplicationStatus.WITHDRAWN));
        assertEquals(1, applications.findByStatusOrderByIdAsc(ApplicationStatus.SUBMITTED).size());
        assertEquals(1, applications.search(null, ApplicationStatus.SUBMITTED, Channel.ONLINE,
                org.springframework.data.domain.PageRequest.of(0, 10)).getTotalElements());
    }

    private static Application sample(String number, Channel channel, ApplicationStatus status) {
        Application application = new Application();
        application.setApplicationNumber(number);
        application.setChannel(channel);
        application.setStatus(status);
        application.setSubmittedAt(Instant.parse("2026-02-01T00:00:00Z"));
        application.setRecordedAt(Instant.parse("2026-02-01T00:00:00Z"));
        application.setFullName("Test Applicant");
        application.setNormalisedName(NameMatching.normalise("Test Applicant"));
        application.setDateOfBirth(LocalDate.of(1990, 1, 15));
        application.setGender(Gender.OTHER);
        application.setNationalId("234567890124");
        application.setAddressLine("1 Test Street");
        application.setCityOrWard("Ward 14");
        application.setAnnualIncome(new BigDecimal("250000"));
        application.setYearsInArea(3);
        application.setStatusLookupKey("TESTKEY1");
        return application;
    }
}
