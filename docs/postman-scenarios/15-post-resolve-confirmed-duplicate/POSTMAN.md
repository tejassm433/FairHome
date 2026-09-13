# Same person: keep one, reject the other

- **Method:** `POST`
- **URL:** `http://localhost:8080/api/admin/duplicates/REPLACE_FLAG_ID/resolve`
- **Auth:** HTTP Basic `admin` / `FairHome@2026`
- **Expected HTTP:** `200`
- Body: `request.json`.

Import `headers.json` as Postman headers. Paste `request.json` as the raw JSON body when the method is POST or PUT.

keep must be one of the two application numbers on that flag.

Replace any `REPLACE` tokens with values from earlier steps (applicationNumber, statusLookupKey, flagId, runId).
