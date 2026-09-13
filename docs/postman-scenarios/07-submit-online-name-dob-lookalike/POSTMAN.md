# Same name + DOB, different valid ID -> queue

- **Method:** `POST`
- **URL:** `http://localhost:8080/api/applications`
- **Auth:** none (public)
- **Expected HTTP:** `201`
- Body: `request.json`.

Import `headers.json` as Postman headers. Paste `request.json` as the raw JSON body when the method is POST or PUT.

Run after 01 so Ananya Reddy 1992-03-14 already exists.

Replace any `REPLACE` tokens with values from earlier steps (applicationNumber, statusLookupKey, flagId, runId).
