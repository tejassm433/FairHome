# FairHome Allocation

## 1. What this app does

FairHome Allocation is a single Java / Spring Boot service for a public housing draw.

The system accepts applications in two ways through **online** public form or when an officer enters a **paper** application. Both go through the same validation and intake process.

It also checks for possible **duplicate** applications. Instead of automatically rejecting a possible duplicate, it sends it to a review queue for an officer to check.

For the flat allocation, the system uses a transparent and repeatable lottery. It generates the result using **SHA-256** with a public seed and the application number, rather than relying on a hidden random generator.

Once the final draw is published, applicants can look up their own placement and see a clear explanation of why they received that placement, including the category or reservation they were considered under and the lottery result that determined their position.

Nothing except **Java 21+** and a browser is required to run this application. The database is an H2 file created next to the process.

| Who | What they use |
| --- | --- |
| Applicant | `/apply`, `/status`, `/rules`, `/verify`, `/results` |
| Officer | `/admin` — paper entry, duplicate queue, rule book, draw, audit |

## 2. How this works ?

## Example: From Application to Flat Allotment

Here is one example of how FairHome works from start to finish.

**Ananya Reddy** applies online. At the same time, there is already a paper application in the system that looks similar to hers. An officer checks both applications, confirms that they belong to two different people, and allows both to take part in the draw. After the draw is published, Ananya can check her result and see why she received her position.

![FairHome HLD — Ananya Reddy's allotment](docs/hld-excalidraw.svg)

### The example, step by step

1. Ananya is 34 years old, belongs to the LIG category, has an annual income of ₹4.2 lakh, and has lived in Ward 12 for 9 years. She submits her application through `/apply`. The system validates her Aadhaar-style ID using the Verhoeff algorithm, determines her LIG category based on her income, and gives her the application number `FH-2026-004128` and receipt number `R8K2-M41Q`.

2. The system finds an existing paper application, `FH-2026-001104`, that looks similar. It has the same date of birth, the name is about 92% similar, and the national ID differs by only one digit. The system does **not** reject either application automatically. Instead, Ananya's application is put into `PENDING_DUPLICATE_REVIEW`, and a duplicate-review task is added to the officer's queue.

3. The officer opens `/admin/duplicates` and compares both applications. After checking the details, the officer decides that they belong to **two different people — both applications can continue**. Both applications are moved back to `SUBMITTED`. The officer's decision, note, and identity are recorded in the audit log. The system will not allow the final draw to run while there are unresolved duplicate cases.

4. The officer first runs a dry run to check the allocation. This does not affect the actual published result. Once everything is ready, the officer runs the final draw and publishes it. Within the LIG category, applications are ordered using `SHA-256(seed + ":" + application number)`. Since Ananya has been living in the area for 9 years, she is considered for the `LOCAL` seats, which are filled first.

5. After the results are published, Ananya goes to `/status` and enters her application number and receipt number. She sees:

   **Allotted · LIG / LOCAL · Serial 47**

   She can also see the step-by-step reason for her placement, including the category and reservation pool she was considered under and how the lottery determined her position.

   Ananya can only see her own result. She cannot access another applicant's details. Since the lottery seed is public, anyone can independently calculate the SHA-256 value for Ananya's application and verify the lottery order.

The same story is also shown visually on `/hld` once the application is running.

## 3. Assumptions

- There are **600 identical flats** in this phase. We do not consider the floor, block, or flat size during allocation.

- The **income category is decided by the system** based on the income entered by the applicant. Applicants cannot choose their own category such as EWS, LIG, MIG, or HIG.

- An **existing resident** is someone who has lived continuously in the specified area for at least 3 years. This period can be changed in the configuration. Existing residents have a reserved quota within each income category. It is not used as a tie breaker.

- **Reserved quotas work along with the main category.** If an applicant qualifies for a reserved quota, they are considered for those reserved flats first. They can also compete for the remaining open flats.

- The system uses the **largest remainder method** to convert percentage based quotas into whole numbers. Reserved seats within each category are rounded down first, so they cannot reduce the number of open seats.

- If some **reserved seats are not filled**, they are added back to the open seats of that category. If some seats in an entire income category remain unfilled, they are redistributed across the scheme based on lottery rank. Both rules are defined in the configuration.

- The **waiting list contains 25% of the seats** available for each category. The number is rounded up when needed.

- When an officer confirms that two applications are duplicates, the **earlier application is kept by default**. The officer can choose to keep the later application instead if there is a valid reason.

- Around **4,000 demo applications** are created when the application starts for the first time. They go through the same intake process as real applications, so the duplicate review queue contains actual results from the duplicate detection logic.

- There is **one shared officer login** for the demo. There is no separate staff directory, different officer roles, password reset, or SSO.

- The application runs as **one process**. Application intake is handled one at a time using a lock. If multiple application instances are used in the future, a database level lock would be needed.

- The application uses **H2 in file mode** instead of keeping everything in memory. This allows the draw and other important data to remain available even after the application is restarted.

## 4. Left out, and why

Some features are intentionally not included because they are outside the scope of this assignment.

- **Real login, OTP, and UIDAI integration.** The demo only needs enough security to keep public users away from the admin and draw controls. In a real government system, `/admin` would be protected using the organisation's identity provider and other security controls.

- **Document upload and certificate verification.** Applicants declare things such as disability or ex-serviceman status. In a real process, an officer would verify the supporting documents. This system does not try to automate that verification.

- **A generic rule engine or rule language.** The system uses a small set of clearly named rules such as `LOCAL_RESIDENT`, `DIFFERENTLY_ABLED`, `WOMAN`, and `EX_SERVICEMAN`. This keeps the rules easy to understand and review. A fully configurable rule language would add complexity and could make it harder to know exactly how an allocation decision was made.

- **Payments, allotment letters, and surrender handling.** These are part of the process after allocation. The system only provides the waiting list so that there is an order to follow if a flat becomes available later. The actual payment and surrender workflow is outside the scope of this project.

- **Applicant notification for duplicate review.** When the system finds a possible duplicate, it places the application in the review queue for an officer. The system does not currently send a notification to the applicant asking them to confirm their details. This can be added later as part of the notification workflow.

- **SC/ST/OBC reservation.** The current design focuses on income categories and reserved quotas such as local residents. Caste based reservation is not included in this version. It can be added later by introducing the required rules and eligibility checks without changing the main allocation flow.

- **A separate tamper proof audit store or HSM.** The audit log is hash chained and stored in the same database as the rest of the application data. This makes unauthorised changes to individual audit records detectable. However, someone with full access to the database could still change the entire chain. For a production system, the audit data would be stored separately with stronger protection. The important artefacts produced by this system are the final results hash and the rule file hash.

- **Multiple application instances and PostgreSQL.** The assignment is designed to run on any machine with Java, so the demo uses H2 in file mode and runs as a single application instance. A production deployment could use PostgreSQL and database level locking when multiple instances need to process applications at the same time.

## 5. How to run this app

Needs **Java 21+**. The app listens on **http://localhost:8080**.

```bash
./mvnw clean install
java -jar target/fairhome.jar
```

Windows: `mvnw.cmd clean install`, then the same `java -jar`.

Open [http://localhost:8080](http://localhost:8080).

On first start the process:

- creates `./data/fairhome.mv.db`
- publishes the rule book in `src/main/resources/rules/default-ruleset.json`
- seeds about 4,000 demo applications, including deliberate duplicates

Empty start (no demo people):

```bash
java -Dfairhome.demo-data.enabled=false -jar target/fairhome.jar
```

Officer console: [http://localhost:8080/admin](http://localhost:8080/admin)

```
username: admin
password: FairHome@2026
```

Change `fairhome.admin.username` and `fairhome.admin.password` in `src/main/resources/application.properties` before this is reachable from anywhere but your own machine.

Useful paths: `/apply`, `/status`, `/rules`, `/hld` (the example board), `/admin`.

Tests (JSON cases under `src/test/resources/cases/`):

```bash
./mvnw test
```

## 6. About the developer

I am **Tejas**, a Java backend developer with about **4 years 6 months** at SunTec Business Solutions (banking and telecom). Day-to-day I work in **Java, Spring Boot, microservices, Kafka, PostgreSQL / Oracle, REST, Docker, Git, Jenkins**.

I like finding the root cause, not only the patch. I built FairHome as a single-jar allocation system to show that same habit on a problem that has to be explained after the result is published.
