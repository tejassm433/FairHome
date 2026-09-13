# Final draw after the queue is clear

- **Method:** `POST`
- **URL:** `http://localhost:8080/api/admin/draws?mode=FINAL&note=official-postman`
- **Auth:** HTTP Basic `admin` / `FairHome@2026`
- **Expected HTTP:** `201`
- Body: none (query/path only).

Import `headers.json` as Postman headers. Paste `request.json` as the raw JSON body when the method is POST or PUT.

Copy id. Also fails 409 if a FINAL is already published.

Replace any `REPLACE` tokens with values from earlier steps (applicationNumber, statusLookupKey, flagId, runId).
