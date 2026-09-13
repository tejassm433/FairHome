# Backend code-flow UML (one folder per Java file)

New files only. Nothing under `src/` was changed.

Open any `FLOW.puml` in a PlantUML viewer (VS Code PlantUML, or https://www.plantuml.com/plantuml).

| Folder | Class |
| --- | --- |
| `01-FairhomeApplication/` | `com.fairhome.FairhomeApplication` |
| `02-FairHomeProperties/` | `com.fairhome.config.FairHomeProperties` |
| `03-SecurityConfig/` | `com.fairhome.config.SecurityConfig` |
| `04-CurrentOfficer/` | `com.fairhome.config.CurrentOfficer` |
| `05-ApplicationForm/` | `com.fairhome.application.ApplicationForm` |
| `06-Application/` | `com.fairhome.application.Application` |
| `07-ApplicationRepository/` | `com.fairhome.application.ApplicationRepository` |
| `08-ApplicationStatus/` | `com.fairhome.application.ApplicationStatus` |
| `09-Channel/` | `com.fairhome.application.Channel` |
| `10-IntakeException/` | `com.fairhome.application.IntakeException` |
| `11-IntakeService/` | `com.fairhome.application.IntakeService` |
| `12-NationalId/` | `com.fairhome.support.NationalId` |
| `13-Verhoeff/` | `com.fairhome.support.Verhoeff` |
| `14-NameMatching/` | `com.fairhome.support.NameMatching` |
| `15-Hashes/` | `com.fairhome.support.Hashes` |
| `16-Display/` | `com.fairhome.support.Display` |
| `17-DedupService/` | `com.fairhome.dedup.DedupService` |
| `18-DuplicateFlag/` | `com.fairhome.dedup.DuplicateFlag` |
| `19-DuplicateFlagRepository/` | `com.fairhome.dedup.DuplicateFlagRepository` |
| `20-MatchType/` | `com.fairhome.dedup.MatchType` |
| `21-DuplicateResolution/` | `com.fairhome.dedup.DuplicateResolution` |
| `22-RuleSetDocument/` | `com.fairhome.rules.RuleSetDocument` |
| `23-RuleSetVersion/` | `com.fairhome.rules.RuleSetVersion` |
| `24-RuleSetRepository/` | `com.fairhome.rules.RuleSetRepository` |
| `25-RuleSetService/` | `com.fairhome.rules.RuleSetService` |
| `26-RuleValidationException/` | `com.fairhome.rules.RuleValidationException` |
| `27-Gender/` | `com.fairhome.rules.Gender` |
| `28-ApplicantPredicates/` | `com.fairhome.rules.ApplicantPredicates` |
| `29-AllocationEngine/` | `com.fairhome.draw.AllocationEngine` |
| `30-DrawService/` | `com.fairhome.draw.DrawService` |
| `31-DrawRun/` | `com.fairhome.draw.DrawRun` |
| `32-DrawRunRepository/` | `com.fairhome.draw.DrawRunRepository` |
| `33-Allocation/` | `com.fairhome.draw.Allocation` |
| `34-AllocationRepository/` | `com.fairhome.draw.AllocationRepository` |
| `35-DrawMode/` | `com.fairhome.draw.DrawMode` |
| `36-Outcome/` | `com.fairhome.draw.Outcome` |
| `37-AuditService/` | `com.fairhome.audit.AuditService` |
| `38-AuditEvent/` | `com.fairhome.audit.AuditEvent` |
| `39-AuditEventRepository/` | `com.fairhome.audit.AuditEventRepository` |
| `40-ApiController/` | `com.fairhome.web.ApiController` |
| `41-ApiExceptionHandler/` | `com.fairhome.web.ApiExceptionHandler` |
| `42-ExportController/` | `com.fairhome.web.ExportController` |
| `43-AdminController/` | `com.fairhome.web.AdminController` |
| `44-PublicController/` | `com.fairhome.web.PublicController` |
| `45-WebErrorController/` | `com.fairhome.web.WebErrorController` |
| `46-DemoDataSeeder/` | `com.fairhome.seed.DemoDataSeeder` |

Postman steps live in `docs/postman-scenarios/`.
