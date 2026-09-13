# Run a dry run (allowed even with an open queue)

- **Method:** `POST`
- **URL:** `http://localhost:8080/api/admin/draws?mode=DRY_RUN&note=postman-rehearsal`
- **Auth:** HTTP Basic `admin` / `FairHome@2026`
- **Expected HTTP:** `201`
- Body: none (query/path only).

Import `headers.json` as Postman headers. Paste `request.json` as the raw JSON body when the method is POST or PUT.

Copy id as runId.

Replace any `REPLACE` tokens with values from earlier steps (applicationNumber, statusLookupKey, flagId, runId).
