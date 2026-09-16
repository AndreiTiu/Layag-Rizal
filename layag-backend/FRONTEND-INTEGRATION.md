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
- **Date/format:** all responses are JSON; status codes are meaningful (200 ok, 400 bad input, 401 no/invalid session, 403 wrong role / not owner / not-assigned rider / unverified, 404 unknown, 413 file too big, 429 lockout).
- **Uploads:** proof-of-delivery photos are sent base64 in the form body (no multipart). Keep them ≤ 3 MB.

## Test accounts
| Login | Password | Role / note |
|---|---|---|
| `admin@layag.ph` | admin123 | ADMIN — dashboard, all payments, search, rider roster |
| `anna2@layag.ph` | layag3a161b | rider VAN (id 36), cap 3 — accepts VAN deliveries |
| `ben2@layag.ph` | (issued by admin) | rider MOTORCYCLE (id 37), cap 2 |
| `mia2@layag.ph` | layag64123a | rider VAN (id 38), cap 4 |
| `juan@layag.ph` | pass123 | SENDER — book parcels, view own receipt/ratings/proofs |
| `andre@layag.ph` | pass123 | SENDER |
| `rbacadmin@layag.ph` | pass123 | ADMIN (RBAC test) |
| `rbaccourier@layag.ph` | pass123 | COURIER |
| `rbacsender@layag.ph` | pass123 | SENDER |

New senders can also register themselves (always SENDER; email OTP first).
Riders are **provisioned by admin** (`POST /api/admin/riders`) — there is no rider self-registration.

## Screen → endpoint map (what to call)
| Screen you build | Call |
|---|---|
| Sign up / verify / Login / Logout | `POST /api/register` · `POST /api/verify` · `POST /api/verify/resend` · `POST /api/login` · `POST /api/logout` |
| Book a delivery | `POST /api/parcels` (fields: sender, receiver, weight, service, vehicle, pickupAddress, dropoffAddress, distanceKm?, interIsland?, paymentMethod CASH/CARD/EWALLET) |
| My deliveries (sender list) | `GET /api/my-parcels` |
| Confirm booking (triggers rider auto-assign) | `POST /api/parcels/confirm` (`code`) |
| Edit / reschedule / cancel | `POST /api/parcels/edit` · `/reschedule` · `/cancel` (owner) |
| Track / timeline (public) | `GET /api/parcels?code=LAYAG-XXXX-XXXX-XXXX[&view=timeline]` |
| Admin: riders / provision rider | `GET /api/admin/riders` · `POST /api/admin/riders` |
| Rider: my profile / jobs / accept / decline | `GET /api/riders/me` · `GET /api/riders/jobs` · `POST /api/riders/jobs/accept` · `POST /api/riders/jobs/decline` (`assignmentId`) |
| Rider: availability toggle / earnings | `POST /api/riders/availability` (`available`) · `GET /api/riders/earnings` |
| Rider: move status | `POST /api/parcels/status` — **only the assigned rider or ADMIN** |
| Rider: cash confirm | `POST /api/payments/confirm-collect` |
| Rider: upload proof photo | `POST /api/proofs` (`code`, `photo` = base64 or data-URI, `notes?`, `recipientName?`) |
| View proof metadata / photo | `GET /api/proofs?code=..` · `GET /api/proofs/photo?code=..` |
| Pay online (CARD/EWALLET) | `POST /api/payments/charge` (mock gateway, idempotent) |
| Payment history (mine / admin) | `GET /api/payments/my` · `GET /api/admin/payments` |
| Receipt | `GET /api/payments/receipt?code=LAYAG-...` (owner or admin) |
| Rate a delivered parcel / view | `POST /api/ratings` · `GET /api/ratings/parcel?code=` · `GET /api/ratings/rider?riderId=` |
| Notifications inbox / read | `GET /api/notifications` · `POST /api/notifications/read` · `/read-all` |
| Admin dashboard | `GET /api/admin/summary` · `GET /api/parcels/search?term=..` · `GET /api/parcels?status=..` |
| Courier GPS: ping / route | `POST /api/gps` · `GET /api/gps?parcelId=N` (draw with Leaflet + OpenStreetMap, no key) |
| Profile | `POST /api/users/profile` |

## UI rules (small, important)
1. New registrations are **always SENDER** — do not render a role picker at sign-up.
2. **Email OTP required**: after register (or on a 403 at login), show a 6-digit code form → `POST /api/verify`; on bad code it can resend (`POST /api/verify/resend`). In offline demo mode the code appears at `GET /api/dev/mailbox` and on the server console.
3. Handle **429** (too many attempts), **401** (expired/logged-out → back to login), **403** (wrong role / not the assigned rider / not the owner), and **413** (photo too large) explicitly.
4. A parcel is not on the road until the sender confirms (auto-assigns a rider). Show the assigned rider's name/vehicle on the parcel screen.
5. Rider work flow: inbox lists jobs → accept → the parcel is now *yours* → only you (or admin) can move its status → upload a proof photo at OUT_FOR_DELIVERY/DELIVERED.

## Running the backend locally
```
java -cp "out;lib\mysql-connector-j-9.7.0.jar" api.ApiServer
```
Copy `config.example.properties` → `config.properties`. Blank `mail.resendApiKey` = offline simulated mail (demo mode); with a key = real Resend emails. See `README.md` for compile + DB setup (`schema.sql` — includes the delivery_proofs table; run the whole file on a fresh DB).