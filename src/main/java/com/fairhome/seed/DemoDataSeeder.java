package com.fairhome.seed;

import com.fairhome.application.ApplicationForm;
import com.fairhome.application.ApplicationRepository;
import com.fairhome.application.IntakeException;
import com.fairhome.application.IntakeService;
import com.fairhome.config.FairHomeProperties;
import com.fairhome.rules.Gender;
import com.fairhome.support.Verhoeff;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Generates a realistic intake on first start so the draw can be exercised straight away.
 *
 * <p>It deliberately goes through {@link IntakeService}, the same path a real applicant or clerk
 * uses, rather than writing rows directly. That means the seeded data is subject to the same
 * validation and duplicate detection as anything else, and the resulting duplicate queue is genuine
 * output of the detector rather than fixtures posing as output.
 *
 * <p>The bulk of the intake is unique people. A small, fixed number of repeat attempts are planted
 * afterwards so the duplicate-review queue has two or three real cases to work through, not hundreds.
 */
@Component
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private static final String[] FIRST_NAMES = {
            "Aarav", "Aditi", "Ananya", "Arjun", "Bhavna", "Chetan", "Divya", "Farhan", "Gauri",
            "Harsh", "Imran", "Ishita", "Jaya", "Kabir", "Kavya", "Lakshmi", "Manish", "Meera",
            "Nikhil", "Nisha", "Omkar", "Pooja", "Pranav", "Radha", "Rahul", "Rekha", "Rohit",
            "Sadiya", "Sanjay", "Shalini", "Sneha", "Suresh", "Tara", "Uday", "Vandana", "Varun",
            "Vikram", "Yamini", "Zoya", "Neha", "Deepak", "Anil", "Sunita", "Ravi", "Priya"
    };

    private static final String[] LAST_NAMES = {
            "Sharma", "Verma", "Iyer", "Nair", "Reddy", "Patel", "Khan", "Das", "Ghosh", "Joshi",
            "Kulkarni", "Pillai", "Mehta", "Bose", "Chauhan", "Naidu", "Rao", "Singh", "Gupta",
            "Bhatt", "Kaur", "Menon", "Shetty", "Deshpande", "Trivedi", "Mishra", "Yadav", "Bakshi"
    };

    private static final String[] WARDS = {
            "Ward 12, North Zone", "Ward 13, North Zone", "Ward 14, North Zone",
            "Ward 15, North Zone", "Ward 16, North Zone", "Ward 17, North Zone",
            "Ward 18, North Zone", "Ward 21, East Zone", "Ward 22, East Zone", "Ward 30, South Zone"
    };

    private static final String[] STREETS = {
            "Nehru Marg", "Gandhi Cross Road", "Station Road", "Lake View Lane", "Mill Colony",
            "Subhash Nagar", "Rose Garden Street", "Old Post Office Road", "Canal Road", "Hill Path"
    };

    private static final String[] CLERKS = {
            "R. Iyer (counter 1)", "S. Bhatt (counter 2)", "M. Fernandes (counter 3)",
            "P. Kulkarni (counter 4)"
    };

    private final ApplicationRepository applications;
    private final IntakeService intakeService;
    private final FairHomeProperties properties;

    public DemoDataSeeder(ApplicationRepository applications, IntakeService intakeService,
                          FairHomeProperties properties) {
        this.applications = applications;
        this.intakeService = intakeService;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        FairHomeProperties.DemoData config = properties.getDemoData();
        if (!config.isEnabled()) {
            log.info("Demo data generation is switched off (fairhome.demo-data.enabled=false).");
            return;
        }
        long existing = applications.count();
        if (existing > 0) {
            log.info("Skipping demo data: {} applications already on file.", existing);
            return;
        }

        long start = System.currentTimeMillis();
        Random random = new Random(config.getSeed().hashCode());
        int target = config.getApplications();

        List<Person> people = new ArrayList<>();
        int accepted = 0;
        int held = 0;
        int rejected = 0;

        for (int i = 0; i < target; i++) {
            Person person = Person.random(random);
            people.add(person);
            IntakeCounts counts = submit(person, random, config.getOfflinePercent());
            accepted += counts.accepted;
            held += counts.held;
            rejected += counts.rejected;

            if ((i + 1) % 500 == 0) {
                log.info("Demo intake progress: {} of {}", i + 1, target);
            }
        }

        int reviewDuplicates = Math.min(Math.max(config.getDuplicateReviewCount(), 0), people.size());
        for (int i = 0; i < reviewDuplicates; i++) {
            Person original = people.get(i);
            // One exact national-ID repeat; the rest mistype a digit so the name+DOB matcher fires.
            Person person = i == 0 ? original : original.withMistypedId(random);
            IntakeCounts counts = submit(person, random, config.getOfflinePercent());
            accepted += counts.accepted;
            held += counts.held;
            rejected += counts.rejected;
        }

        log.info("Demo data ready in {} ms: {} applications recorded ({} held for duplicate review, "
                        + "{} refused at intake), {} distinct people, {} planted review duplicates.",
                System.currentTimeMillis() - start, accepted, held, rejected, people.size(),
                reviewDuplicates);
    }

    private IntakeCounts submit(Person person, Random random, int offlinePercent) {
        boolean offline = random.nextInt(100) < offlinePercent;
        ApplicationForm form = person.toForm(random, offline);
        try {
            IntakeService.Receipt receipt = offline
                    ? intakeService.recordOffline(form, "demo-seed")
                    : intakeService.submitOnline(form);
            return new IntakeCounts(1, receipt.heldForReview() ? 1 : 0, 0);
        } catch (IntakeException e) {
            return new IntakeCounts(0, 0, 1);
        }
    }

    private record IntakeCounts(int accepted, int held, int rejected) {
    }

    /** A synthetic applicant, kept so that repeat submissions can reuse the same identity. */
    private record Person(String firstName, String lastName, LocalDate dateOfBirth, Gender gender,
                          String nationalId, String phone, String email, int yearsInArea,
                          BigDecimal income, boolean differentlyAbled, boolean exServiceman,
                          String ward, String street, int houseNumber) {

        static Person random(Random random) {
            String first = FIRST_NAMES[random.nextInt(FIRST_NAMES.length)];
            String last = LAST_NAMES[random.nextInt(LAST_NAMES.length)];
            Gender gender = random.nextInt(100) < 46 ? Gender.FEMALE
                    : random.nextInt(100) < 97 ? Gender.MALE : Gender.OTHER;
            LocalDate dob = LocalDate.of(1957 + random.nextInt(48), 1 + random.nextInt(12),
                    1 + random.nextInt(28));
            String phone = "9" + (100000000 + random.nextInt(899999999));
            String email = (first + "." + last + random.nextInt(900)).toLowerCase(Locale.ROOT)
                    + "@example.in";

            // A little over half the applicants are long-standing residents of the scheme wards, which
            // is what makes the local-resident reserved quota interesting rather than trivially filled.
            int years = random.nextInt(100) < 55 ? 3 + random.nextInt(25) : random.nextInt(3);

            BigDecimal income = drawIncome(random);

            return new Person(first, last, dob, gender, randomNationalId(random), phone, email, years,
                    income, random.nextInt(1000) < 38, random.nextInt(1000) < 25,
                    WARDS[random.nextInt(WARDS.length)], STREETS[random.nextInt(STREETS.length)],
                    1 + random.nextInt(400));
        }

        /** Skewed towards the lower bands, which is where the real demand in such a scheme sits. */
        private static BigDecimal drawIncome(Random random) {
            int bucket = random.nextInt(100);
            if (bucket < 34) {
                return BigDecimal.valueOf(60000 + random.nextInt(240000));
            }
            if (bucket < 71) {
                return BigDecimal.valueOf(300001 + random.nextInt(299999));
            }
            if (bucket < 94) {
                return BigDecimal.valueOf(600001 + random.nextInt(599999));
            }
            return BigDecimal.valueOf(1200001 + random.nextInt(1500000));
        }

        private static String randomNationalId(Random random) {
            StringBuilder payload = new StringBuilder();
            payload.append(2 + random.nextInt(8));
            for (int i = 0; i < 10; i++) {
                payload.append(random.nextInt(10));
            }
            return payload + String.valueOf(Verhoeff.checkDigitFor(payload.toString()));
        }

        /**
         * Someone who reapplied and got a digit wrong the second time. The national ID no longer
         * matches, so only the fuzzy name and date of birth check can catch this pair.
         */
        Person withMistypedId(Random random) {
            char[] digits = nationalId.toCharArray();
            int position = 1 + random.nextInt(10);
            char original = digits[position];
            char replacement = (char) ('0' + ((original - '0' + 1 + random.nextInt(8)) % 10));
            digits[position] = replacement;
            String payload = new String(digits, 0, 11);
            String retyped = payload + Verhoeff.checkDigitFor(payload);
            return new Person(firstName, lastName, dateOfBirth, gender, retyped, phone, email,
                    yearsInArea, income, differentlyAbled, exServiceman, ward, street, houseNumber);
        }

        ApplicationForm toForm(Random random, boolean offline) {
            ApplicationForm form = new ApplicationForm();

            // Paper entries pick up honorifics and initials that the online form usually does not,
            // which is exactly the noise the name normaliser has to absorb.
            String name = offline && random.nextInt(100) < 30
                    ? (gender == Gender.FEMALE ? "Smt. " : "Shri ") + firstName + " " + lastName
                    : firstName + " " + lastName;

            form.setFullName(name);
            form.setDateOfBirth(dateOfBirth);
            form.setGender(gender);
            form.setNationalId(formatId(nationalId, offline, random));
            form.setPhone(phone);
            form.setEmail(offline && random.nextInt(100) < 45 ? null : email);
            form.setAddressLine(houseNumber + ", " + street);
            form.setCityOrWard(ward);
            form.setPostalCode(String.valueOf(400001 + random.nextInt(90)));
            form.setAnnualIncome(income);
            form.setYearsInArea(yearsInArea);
            form.setDifferentlyAbled(differentlyAbled);
            form.setExServiceman(exServiceman);
            form.setFirstTimeHomeBuyer(random.nextInt(100) < 88);
            form.setDeclarationAccepted(true);

            if (offline) {
                form.setRecordedBy(CLERKS[random.nextInt(CLERKS.length)]);
                form.setPaperReference("PF/2026/" + (10000 + random.nextInt(89999)));
                form.setPaperSubmittedOn(LocalDate.of(2026, 1, 20).plusDays(random.nextInt(40)));
            }
            return form;
        }

        /** Paper entries arrive with the ID grouped in fours, online ones usually do not. */
        private String formatId(String id, boolean offline, Random random) {
            if (offline && random.nextInt(100) < 60) {
                return id.substring(0, 4) + " " + id.substring(4, 8) + " " + id.substring(8);
            }
            return id;
        }
    }
}
