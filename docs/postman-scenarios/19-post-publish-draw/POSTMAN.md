# Publish a FINAL run as the official result

- **Method:** `POST`
- **URL:** `http://localhost:8080/api/admin/draws/REPLACE_RUN_ID/publish`
- **Auth:** HTTP Basic `admin` / `FairHome@2026`
- **Expected HTTP:** `200`
- Body: none (query/path only).

Import `headers.json` as Postman headers. Paste `request.json` as the raw JSON body when the method is POST or PUT.

Dry runs cannot be published.

Replace any `REPLACE` tokens with values from earlier steps (applicationNumber, statusLookupKey, flagId, runId).
