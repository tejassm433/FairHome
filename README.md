# FairHome

FairHome is a Java and Spring Boot application for allocating 600 public housing flats through a fair and transparent lottery process.

Applications can come from two sources:

* Online application submitted by the applicant
* Paper application entered by an officer

Both use the same intake and validation flow.

The system also detects possible duplicate applications and sends them to an officer for review instead of automatically rejecting them.

The final allocation uses a reproducible SHA-256 based lottery, so the published result can be independently verified.

---

## 1. How It Works

```text
Online Application ──┐
                     ├──> Intake & Validation
Paper Application ───┘          │
                                ↓
                        Duplicate Detection
                                │
                    ┌───────────┴───────────┐
                    │                       │
                 No Match             Possible Match
                    │                       │
                    │                 Officer Review
                    │                       │
                    └───────────┬───────────┘
                                ↓
                           Final Draw
                                ↓
                            Allocation
                                ↓
                             Result
```

### Example

An applicant submits an online application.

The system validates the application, derives the income category from the declared income, and checks for possible duplicates.

If a similar paper application already exists, the new application is held for officer review.

The officer can decide that they are different people, in which case both applications continue.

Once all duplicate reviews are completed, the officer can run the final draw.

The applicant can then use their application number and receipt to view their result and the reason for their placement.

---

## 2. Lottery

The final lottery is deterministic and does not depend on a hidden random number generator.

For each eligible application:

```text
SHA-256(publicSeed + ":" + applicationNumber)
```

The resulting value is used to order applications within the applicable allocation pool.

The public seed is stored with the published draw, which allows the lottery order to be reproduced later.

The `Random` used while starting the application is only used to generate demo data. It is not used for the final allocation.

---

## 3. Allocation Rules

* There are 600 identical flats in the scheme.
* The income category is derived from declared income. Applicants cannot select their category.
* An existing resident is someone with at least 3 continuous years in the published area.
* Local residents have a reserved quota within each income category.
* Applicants who qualify for a reserved quota can also compete for open seats.
* Largest remainder is used when converting percentages into whole seats.
* Reserved seats are rounded down before allocating the remaining seats to the open pool.
* Unfilled reserved seats return to that category's open pool.
* Unfilled category seats can be redistributed scheme wide according to lottery rank.
* The waiting list is 25% of each category's seats, rounded up.
* When a duplicate is confirmed, the earlier application is kept by default unless the officer chooses otherwise.

The allocation rules are defined in:

```text
src/main/resources/rules/default-ruleset.json
```

---

## 4. Duplicate Detection

A possible duplicate is not automatically rejected.

The system compares information such as:

* National ID
* Name
* Date of birth
* Phone
* Email

Possible matches are placed in the officer review queue.

The officer can decide whether the applications belong to the same person or to different people.

The decision is recorded in the audit trail.

A final draw cannot be published while duplicate reviews are still open.

---

## 5. Audit Trail

Important actions are recorded in a hash chained audit log.

Each entry contains the hash of the previous entry and its own calculated hash.

```text
Entry 1 → Hash 1
             ↓
Entry 2 + Hash 1 → Hash 2
                       ↓
Entry 3 + Hash 2 → Hash 3
```

This makes changes to individual audit records detectable.

The audit data is stored in the H2 database for this assignment.

---

## 6. Demo Data

When the application starts with an empty database, it generates around 4,000 demo applications.

The demo data goes through the real application intake flow rather than being inserted directly into the database.

It includes both online and paper applications and a few intentionally created duplicate cases so that the duplicate review flow can be demonstrated.

Demo data generation is controlled by:

```properties
fairhome.demo-data.enabled=true
fairhome.demo-data.applications=4000
fairhome.demo-data.duplicate-review-count=3
fairhome.demo-data.offline-percent=35
fairhome.demo-data.seed=fairhome-demo-2026
```

To start without demo data:

```bash
java -Dfairhome.demo-data.enabled=false -jar target/fairhome.jar
```

---

## 7. Technology

* Java 21
* Spring Boot
* Spring Data JPA
* H2
* Maven
* JUnit
* SHA-256
* JSON based configuration

The application runs as a single Spring Boot process.

H2 is used in file mode so data survives application restarts.

---

## 8. Running the Application

### Requirements

* Java 21+
* Maven or Maven Wrapper

No separate database installation is required.

### Build

Windows:

```bash
mvnw.cmd clean install
```

Linux/macOS:

```bash
./mvnw clean install
```

### Start

```bash
java -jar target/fairhome.jar
```

Open:

```text
http://localhost:8080
```

---

## 9. Useful Pages

| Page                | Purpose                         |
| ------------------- | ------------------------------- |
| `/apply`            | Submit an application           |
| `/status`           | Check an application and result |
| `/rules`            | View the active rules           |
| `/verify`           | Verify published information    |
| `/results`          | View published results          |
| `/hld`              | View the system flow            |
| `/admin`            | Officer console                 |
| `/admin/duplicates` | Review possible duplicates      |

### Demo Officer Login

```text
Username: admin
Password: FairHome@2026
```

These credentials are only for local evaluation.

---

## 10. Tests

Test cases are available under:

```text
src/test/resources/cases/
```

Run:

```bash
mvnw.cmd test
```

or:

```bash
./mvnw test
```

---

## 11. Assumptions

* This assignment focuses on application intake, eligibility, duplicate review, allocation, and result explanation.
* Disability and ex-serviceman status are declared but supporting documents are not verified by the system.
* A single officer account is used for the demo.
* The application runs as one process.
* H2 is used instead of PostgreSQL to keep the project easy to run on a clean machine.
* The audit log is stored in the same database. A production system would use stronger protection and separate audit storage.

---

## 12. Left out, and why

Some features are intentionally not included because they are outside the scope of this assignment.

- **Real login, OTP, and UIDAI integration.** The demo only needs enough security to keep public users away from the admin and draw controls. In a real government system, `/admin` would be protected using the organisation's identity provider and other security controls.

- **Document upload and certificate verification.** Applicants declare things such as disability or ex-serviceman status. In a real process, an officer would verify the supporting documents. This system does not try to automate that verification.

- **A generic rule engine or rule language.** The system uses a small set of clearly named rules such as `LOCAL_RESIDENT`, `DIFFERENTLY_ABLED`, `WOMAN`, and `EX_SERVICEMAN`. This keeps the rules easy to understand and review. A fully configurable rule language would add complexity and could make it harder to know exactly how an allocation decision was made.

- **Payments, allotment letters, and surrender handling.** These are part of the process after allocation. The system only provides the waiting list so that there is an order to follow if a flat becomes available later. The actual payment and surrender workflow is outside the scope of this project.

- **Applicant notification for duplicate review.** When the system finds a possible duplicate, it places the application in the review queue for an officer. The system does not currently send a notification to the applicant asking them to confirm their details. This can be added later as part of the notification workflow.

- **A separate tamper proof audit store or HSM.** The audit log is hash chained and stored in the same database as the rest of the application data. This makes unauthorised changes to individual audit records detectable. However, someone with full access to the database could still change the entire chain. For a production system, the audit data would be stored separately with stronger protection. The important artefacts produced by this system are the final results hash and the rule file hash.

- **Multiple application instances and PostgreSQL.** The assignment is designed to run on any machine with Java, so the demo uses H2 in file mode and runs as a single application instance. A production deployment could use PostgreSQL and database level locking when multiple instances need to process applications at the same time.


These can be added later without changing the main allocation flow.

---

## 13. Project Structure

```text
src/
 ├── main/
 │   ├── java/
 │   │   └── .../
 │   └── resources/
 │       ├── rules/
 │       │   └── default-ruleset.json
 │       └── application.properties
 │
 └── test/
     └── resources/
         └── cases/

docs/
 └── hld-excalidraw.svg
```

---

## 14. About

I am Tejas, a Java backend developer with around 4.5 years of experience working with Java, Spring Boot, microservices, Kafka, databases, REST APIs, Docker, and Jenkins.

For this assignment, I focused on keeping the business rules clear, making the lottery reproducible, handling possible duplicates through officer review, and maintaining an audit trail for important decisions.
