# Wrong receipt code is 404, not a leak

- **Method:** `GET`
- **URL:** `http://localhost:8080/api/applications/FH-2026-REPLACE/status?referenceCode=WRONG`
- **Auth:** none (public)
- **Expected HTTP:** `404`
- Body: none (query/path only).

Import `headers.json` as Postman headers. Paste `request.json` as the raw JSON body when the method is POST or PUT.



Replace any `REPLACE` tokens with values from earlier steps (applicationNumber, statusLookupKey, flagId, runId).
