# com.fairhome.application.IntakeService

**File:** `src/main/java/com/fairhome/application/IntakeService.java`

Single door: validate ID/age/income/declaration, lock, dedup, persist, audit, receipt.

**Call path**

- ApiController.register -> submitOnline -> NationalId -> DedupService -> persist -> AuditService -> Receipt

This folder is documentation only. It does not change the Java class.
