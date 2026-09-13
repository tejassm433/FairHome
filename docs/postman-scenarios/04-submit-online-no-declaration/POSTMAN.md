# Reject when declarationAccepted is false

- **Method:** `POST`
- **URL:** `http://localhost:8080/api/applications`
- **Auth:** none (public)
- **Expected HTTP:** `422`
- Body: `request.json`.

Import `headers.json` as Postman headers. Paste `request.json` as the raw JSON body when the method is POST or PUT.



Replace any `REPLACE` tokens with values from earlier steps (applicationNumber, statusLookupKey, flagId, runId).
