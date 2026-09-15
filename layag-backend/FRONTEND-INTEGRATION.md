# LAYAG Backend → Frontend Integration (Lead Programmer's Quick Start)

Everything you need to connect the web app to the live API.

## The one rule that causes most bugs
The API reads **form-encoded** bodies, **not JSON**.
Always send `x-www-form-urlencoded`:

```js
fetch("http://localhost:8080/api/login", {
  method: "POST",
  headers: { "Content-Type": "application/x-www-form-urlencoded" },
  body: new URLSearchParams({ email: "admin@layag.ph", password: "pass123" }),
});
```
Sending a JSON object → the API says `"Name is required."` or similar — that is NOT a backend bug.

## Connection facts
- **Base URL:** `http://localhost:8080`
- **Serving page:** run the web app on **http://localhost:8081** (it is already in the CORS whitelist). `file://` works in dev too.
- **Auth:** after login, send the token on every protected call:
  `headers: { "Authorization": "Bearer <token>" }`
- **Date/format:** all responses are JSON; status codes are meaningful (200 ok, 400 bad input, 401 no/invalid session, 403 wrong role / not owner / unverified, 404 unknown, 429 lockout).

## Test accounts (password: `pass123`)
| Login | Role |
|---|---|
| `admin@layag.ph` | ADMIN — dashboard, all payments, search |
| `cora@layag.ph` | COURIER — status moves, cash confirm, GPS pings |
| `juan@layag.ph` | SENDER — book parcels, view own receipt |
| `rbacadmin@layag.ph` | ADMIN |
| `rbaccourier@layag.ph` | COURIER |
| `rbacsender@layag.ph` | SENDER |

## Screen → endpoint map (what to call)
| Screen you build | Call |
|---|---|
| Sign up / Login / Logout | `POST /api/register` · `POST /api/verify` · `POST /api/verify/resend` · `POST /api/login` · `POST /api/logout` |
| Book a delivery | `POST /api/parcels` |
| Track / timeline (public) | `GET /api/parcels?code=LAYAG-XXXX-XXXX-XXXX[&view=timeline]` |
| Courier: move status | `POST /api/parcels/status` (COURIER/ADMIN) |
| Courier: confirm COD cash | `POST /api/payments/confirm-collect` |
| Courier GPS: ping / route | `POST /api/gps` · `GET /api/gps?parcelId=N` (draw with Leaflet + OpenStreetMap, no key) |
| Pay online | `POST /api/payments/charge` (mock gateway, idempotent) |
| Receipt | `GET /api/payments/receipt?code=LAYAG-...` (owner or admin) |
| Admin dashboard | `GET /api/admin/summary` · `GET /api/admin/payments` · `GET /api/parcels/search?term=..` · `GET /api/parcels?status=..` |
| Profile | `POST /api/users/profile` |

> Note: `GET /api/parcels` currently lists by tracking code. A sender-scoped
> "My deliveries" endpoint (`/api/my-parcels`) is planned — it does not exist yet.

## UI rules (small, important)
1. New registrations are **always SENDER** — do not render a role picker at sign-up.
2. **Email OTP required**: after register (or on a 403 at login), show a 6-digit code form → `POST /api/verify`; on bad code it can resend (`POST /api/verify/resend`). In offline demo mode the code appears at `GET /api/dev/mailbox` (lowercase-filter by email) and on the server console.
3. Handle **429** (try again later) and **401** (expired/logged-out → send back to login) explicitly.
4. Duplicate email → show `"Email already registered."` verbatim.

## Running the backend locally
```
java -cp "out;lib\mysql-connector-j-9.7.0.jar" api.ApiServer
```
Copy `config.example.properties` → `config.properties`. Blank `mail.resendApiKey` = offline simulated mail (demo mode); with a key = real Resend emails. See `README.md` for compile + DB setup (`schema.sql`).