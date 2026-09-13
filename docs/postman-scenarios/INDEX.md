# Postman scenarios (one folder per step)

Run the app on http://localhost:8080 first (`java -jar target/fairhome.jar`).

Import `FairHome.postman_collection.json` into Postman, or open each folder and copy `request.json` + `headers.json`.

Admin calls use HTTP Basic:

- username: `admin`
- password: `FairHome@2026`

Suggested order: 01 -> 05 -> 06 or 07 -> 13 -> 14 or 15 -> 16 -> 17 -> (clear queue) 18 -> 19 -> 08 -> 21 -> 22 -> 23.

Valid sample national IDs (Verhoeff, do not start with 0 or 1):

- `234567890124` (Ananya, scenarios 01 and 06)
- `345678901238` (Rahul paper, scenario 05)
- `456789012341` (underage body, still structurally valid)
- `567890123458` (no-declaration body)
- `678901234560` (name+DOB lookalike)
- `234567890120` is deliberately invalid (scenario 02)

These folders are documentation only. No Java file was modified.
