# Submit a clean online application

- **Method:** `POST`
- **URL:** `http://localhost:8080/api/applications`
- **Auth:** none (public)
- **Expected HTTP:** `201`
- Body: `request.json`.

Import `headers.json` as Postman headers. Paste `request.json` as the raw JSON body when the method is POST or PUT.

Copy applicationNumber and statusLookupKey from the 201 body. You need them for status calls.

Replace any `REPLACE` tokens with values from earlier steps (applicationNumber, statusLookupKey, flagId, runId).
