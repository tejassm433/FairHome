# com.fairhome.config.CurrentOfficer

**File:** `src/main/java/com/fairhome/config/CurrentOfficer.java`

Reads the signed-in username (or the configured officer-name fallback) for the audit actor.

**Call path**

- SecurityContext -> CurrentOfficer.name -> AuditService.record actor

This folder is documentation only. It does not change the Java class.
