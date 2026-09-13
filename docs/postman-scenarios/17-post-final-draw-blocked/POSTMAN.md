# Final draw refused while flags are open

- **Method:** `POST`
- **URL:** `http://localhost:8080/api/admin/draws?mode=FINAL&note=should-fail`
- **Auth:** HTTP Basic `admin` / `FairHome@2026`
- **Expected HTTP:** `409`
- Body: none (query/path only).

Import `headers.json` as Postman headers. Paste `request.json` as the raw JSON body when the method is POST or PUT.

Run this BEFORE clearing the queue, or after seeding demo data.

Replace any `REPLACE` tokens with values from earlier steps (applicationNumber, statusLookupKey, flagId, runId).
