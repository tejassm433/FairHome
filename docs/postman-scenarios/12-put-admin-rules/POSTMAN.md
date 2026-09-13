# Publish a new rule version (HTTP Basic)

- **Method:** `PUT`
- **URL:** `http://localhost:8080/api/admin/rules`
- **Auth:** HTTP Basic `admin` / `FairHome@2026`
- **Expected HTTP:** `200`
- Body: `request.json`.

Import `headers.json` as Postman headers. Paste `request.json` as the raw JSON body when the method is POST or PUT.

This creates a new version. Demo draws already on file keep their old hash.

Replace any `REPLACE` tokens with values from earlier steps (applicationNumber, statusLookupKey, flagId, runId).
