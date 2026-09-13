# com.fairhome.application.Application

**File:** `src/main/java/com/fairhome/application/Application.java`

Persisted claim. Holds status, masked ID, lookup key, channel.

**Call path**

- IntakeService.toEntity -> ApplicationRepository.save -> later Dedup/Draw read it

This folder is documentation only. It does not change the Java class.
