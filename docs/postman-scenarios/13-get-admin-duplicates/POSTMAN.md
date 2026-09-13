# List open duplicate flags

- **Method:** `GET`
- **URL:** `http://localhost:8080/api/admin/duplicates`
- **Auth:** HTTP Basic `admin` / `FairHome@2026`
- **Expected HTTP:** `200`
- Body: none (query/path only).

Import `headers.json` as Postman headers. Paste `request.json` as the raw JSON body when the method is POST or PUT.

Copy flagId for resolve steps.

Replace any `REPLACE` tokens with values from earlier steps (applicationNumber, statusLookupKey, flagId, runId).
