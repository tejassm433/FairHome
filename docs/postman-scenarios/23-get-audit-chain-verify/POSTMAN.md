# Verify the hash-chained audit log

- **Method:** `GET`
- **URL:** `http://localhost:8080/api/admin/audit/verify`
- **Auth:** HTTP Basic `admin` / `FairHome@2026`
- **Expected HTTP:** `200`
- Body: none (query/path only).

Import `headers.json` as Postman headers. Paste `request.json` as the raw JSON body when the method is POST or PUT.



Replace any `REPLACE` tokens with values from earlier steps (applicationNumber, statusLookupKey, flagId, runId).
