# Officer keys a paper form (HTTP Basic)

- **Method:** `POST`
- **URL:** `http://localhost:8080/api/admin/applications/offline`
- **Auth:** HTTP Basic `admin` / `FairHome@2026`
- **Expected HTTP:** `201`
- Body: `request.json`.

Import `headers.json` as Postman headers. Paste `request.json` as the raw JSON body when the method is POST or PUT.

Save applicationNumber for later compare in the duplicate queue.

Replace any `REPLACE` tokens with values from earlier steps (applicationNumber, statusLookupKey, flagId, runId).
