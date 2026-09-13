# Second form with the same national ID is held, not rejected

- **Method:** `POST`
- **URL:** `http://localhost:8080/api/applications`
- **Auth:** none (public)
- **Expected HTTP:** `201`
- Body: `request.json`.

Import `headers.json` as Postman headers. Paste `request.json` as the raw JSON body when the method is POST or PUT.

Requires scenario 01 first (same nationalId 234567890124). Then GET /api/admin/duplicates.

Replace any `REPLACE` tokens with values from earlier steps (applicationNumber, statusLookupKey, flagId, runId).
