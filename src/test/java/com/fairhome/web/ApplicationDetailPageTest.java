package com.fairhome.web;

import com.fairhome.application.Application;
import com.fairhome.application.ApplicationRepository;
import com.fairhome.application.ApplicationStatus;
import com.fairhome.application.Channel;
import com.fairhome.rules.Gender;
import com.fairhome.support.NameMatching;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:appdetail;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "fairhome.demo-data.enabled=false"
})
class ApplicationDetailPageTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationRepository applications;

    @Test
    @Transactional
    void officerCanOpenAnApplicationDetail() throws Exception {
        Application application = new Application();
        application.setApplicationNumber("FH-2026-004033");
        application.setChannel(Channel.ONLINE);
        application.setStatus(ApplicationStatus.SUBMITTED);
        application.setSubmittedAt(Instant.parse("2026-02-01T00:00:00Z"));
        application.setRecordedAt(Instant.parse("2026-02-01T00:00:00Z"));
        application.setFullName("Detail Page Applicant");
        application.setNormalisedName(NameMatching.normalise("Detail Page Applicant"));
        application.setDateOfBirth(LocalDate.of(1990, 1, 15));
        application.setGender(Gender.FEMALE);
        application.setNationalId("234567890124");
        application.setAddressLine("1 Test Street");
        application.setCityOrWard("Ward 14");
        application.setAnnualIncome(new BigDecimal("250000"));
        application.setYearsInArea(4);
        application.setStatusLookupKey("DETAILKEY");
        applications.save(application);

        mockMvc.perform(get("/admin/applications/FH-2026-004033").accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/application-detail"));
    }
}
