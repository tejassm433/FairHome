# Recompute SHA-256(seed : applicationNumber)

- **Method:** `GET`
- **URL:** `http://localhost:8080/api/verify?applicationNumber=FH-2026-004128`
- **Auth:** none (public)
- **Expected HTTP:** `200`
- Body: none (query/path only).

Import `headers.json` as Postman headers. Paste `request.json` as the raw JSON body when the method is POST or PUT.

Replace the application number with a real FH-... from step 01.

Replace any `REPLACE` tokens with values from earlier steps (applicationNumber, statusLookupKey, flagId, runId).
