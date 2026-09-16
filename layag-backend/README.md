# LAYAG Backend (plain Java + JDBC + MySQL)

Self-contained REST API for the LAYAG courier & logistics system. Serves
`localhost:8080`. The web pages (React landing page on 8081, and the customer
page) call this API with `fetch()` using **form-encoded** bodies
(`URLSearchParams`), not JSON.

## Requirements
- JDK 25
- XAMPP MySQL running on `localhost:3306` (root, no password by default)
- `mysql-connector-j-9.7.0.jar` in `lib/`

## Setup
1. Copy `config.example.properties` → `config.properties` and edit values.
   Leave `mail.resendApiKey` blank to run fully offline with the simulated
   `DemoMailer` (codes shown on the console and at `/api/dev/mailbox`).
2. Create the schema:
   ```
   mysql -u root < schema.sql
   ```
3. Compile:
   ```
   javac -encoding UTF-8 -cp "lib\mysql-connector-j-9.7.0.jar" -d out src\api\*.java src\config\*.java src\db\*.java src\mail\*.java src\model\*.java src\repository\*.java src\security\*.java src\service\*.java src\*.java
   ```
4. Run:
   ```
   java -cp "out;lib\mysql-connector-j-9.7.0.jar" api.ApiServer
   ```

## Essential endpoints
See `API-REFERENCE.md` for the full contract. Quick map:

| Method + path | Purpose |
|---|---|
| POST `/api/register` | sign up (role is always SENDER) + sends email OTP |
| POST `/api/verify`, `/api/verify/resend` | confirm / resend the 6-digit code |
| POST `/api/login`, `/api/logout` | session token issue / invalidation |
| POST `/api/parcels` | book a delivery (COD / ONLINE) |
| GET `/api/parcels?code=..[&view=timeline]` | public tracking / timeline |
| POST `/api/parcels/status` | COURIER/ADMIN: move PICKED_UP→IN_TRANSIT→DELIVERED |
| POST `/api/payments/charge` | ONLINE payment (mock gateway, idempotent) |
| POST `/api/payments/confirm-collect` | COD cash confirmation |
| GET `/api/payments/receipt?code=..` | itemized receipt (owner/admin) |
| GET `/api/admin/summary`, `/api/admin/payments` | admin dashboard |
| POST/GET `/api/gps` | courier pings / route points for the map |
| GET `/api/dev/mailbox` | simulated inbox (only in demo mode) |

## Security model (why)
- Registration is always SENDER — `role` is ignored (no self-serve admin).
- Email OTP must be confirmed before login (`403` until verified).
- One generic `"Invalid email or password."` — no account enumeration.
- Payment/receipt routes are owner- or admin-scoped (`requireOwner`).
- Brute-force lockout binds to the real socket IP (`X-Forwarded-For` ignored).
- Passwords PBKDF2-HmacSHA256 (per-account salt); legacy hashes auto-upgrade.

## Seed accounts
`admin@layag.ph` / `admin123` (ADMIN) · `juan@layag.ph` / `andre@layag.ph`
(password `pass123`, SENDER). Riders/provisioned via `POST /api/admin/riders`:
`anna2@layag.ph` (VAN, cap 3), `ben2@layag.ph` (MOTORCYCLE, cap 2),
`mia2@layag.ph` (VAN, cap 4). No self-serve admin/courier — see schema.sql seed.