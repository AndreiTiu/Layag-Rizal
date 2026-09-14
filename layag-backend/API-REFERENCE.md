# LAYAG API Reference (Backend)

**Base URL:** `http://localhost:8080`
**Format:** requests = `key=value&key=value` (form data), responses = JSON
**CORS:** whitelist only — default origin `http://localhost:8081`; configure `cors.allowedOrigins` in `config.properties`. `file://` pages (Origin `null`) are allowed in development only. Unknown origins get `403` and no CORS headers.
**To start backend:** `java -cp "out;lib\mysql-connector-j-9.7.0.jar" api.ApiServer`

**Config:** `config.properties` (gitignored) → env vars (`LAYAG_*`) → defaults.
`mail.resendApiKey` (env `LAYAG_MAIL_RESEND_APIKEY`) switches OTP delivery: blank =
simulated `DemoMailer` (console + `/api/dev/mailbox`, offline), set = **real OTP**
via the Resend REST API. `/api/dev/mailbox` returns `404` whenever a real mailer
is active. Production mode requires the key.
Keys: `db.url`, `db.user`, `db.password`, `http.threads` (bounded thread pool,
default 10), `cors.allowedOrigins`, `security.login.maxFailures`/`lockMs`,
`security.register.maxFailures`. Set `production=true` to refuse defaults.
Browser preflights (`OPTIONS`) are handled automatically against the whitelist.

---

## 0. Authentication (role-based access control)

Login returns a **token** (session, valid 24h). Send it on protected endpoints:
```
Authorization: Bearer <token>
```

| Endpoint | Protection |
|---|---|
| `POST /api/register` | public (role always forced to `SENDER`) |
| `POST /api/login` | public (unverified accounts → `403`) |
| `POST /api/verify` / `POST /api/verify/resend` | public |
| `POST /api/logout` | logged-in user (invalidates token server-side) |
| `GET /api/dev/mailbox` | dev only (simulated email inbox; `404` in production) |
| `GET /api/parcels?...` (track by `id`/`code`, timeline) | public — customers track without logging in |
| `POST /api/parcels` | any logged-in user |
| `POST /api/parcels/status` | `COURIER` or `ADMIN` |
| `POST /api/payments/charge` | logged-in user **and parcel owner** (or `ADMIN`) |
| `POST /api/payments/confirm-collect` | `COURIER` or `ADMIN` |
| `GET /api/payments/parcel` | logged-in user **and parcel owner** (or `ADMIN`) |
| `GET /api/payments/receipt` | logged-in user **and parcel owner** (or `ADMIN`) |
| `GET /api/admin/payments` | `ADMIN` |
| `GET /api/parcels/search` | `ADMIN` |
| `GET /api/parcels?status=...` | `ADMIN` |
| `GET /api/admin/summary` | `ADMIN` |
| `POST /api/users/profile` | logged-in user (edits own profile) |
| `POST/GET /api/gps` | open in demo (courier GPS page) — see note |

Missing/invalid token → `401`. Wrong role → `403`. Not parcel owner → `403`.
No token attached? never succeeds on protected endpoint.
Too many failed logins/registrations from one IP → `429` (brute-force lockout,
configurable via `security.login.maxFailures` / `security.login.lockMs`).
**`X-Forwarded-For` is ignored for rate limiting** — the client can't rotate
that header to escape the lockout; the socket IP is used.
Login failure replies are deliberately generic (`"Invalid email or password."`)
so an attacker can't tell which emails are registered.

---

## 1. Register — POST `/api/register`
| Param | Required | Notes |
|---|---|---|
| name | yes | Full name |
| email | yes | Must be unique; trimmed + lowercased before storing |
| password | yes | Min **6 chars**; stored hashed (PBKDF2-HmacSHA256; legacy SHA-256 hashes auto-upgraded on first login) |
| role | ignored | Public sign-up is **always `SENDER`** — sending `role=ADMIN` etc. is ignored |

Every new account starts **unverified**: a 6-digit code is "sent" (DemoMailer,
10-min TTL, 5 attempts) and the account cannot log in until confirmed via
`POST /api/verify`. Existing accounts were backfilled as verified.

Success: `{"status":"ok","verified":false,"email":"..","message":"User registered. A 6-digit verification code was sent to ..."}`
Bad input → `400` (e.g. password < 6 chars). Duplicate email → `400` `"Email already registered."`
Too many attempts from one IP → `429`

## 2. Login — POST `/api/login`
| Param | Required |
|---|---|
| email | yes |
| password | yes |

Success: `{"status":"ok","userId":1,"name":"Maria","role":"SENDER","token":"a59eca091de8420583075cf90102a62d"}`

Keep the `token`; send it as `Authorization: Bearer <token>`.
Wrong email/password → `401` `"Invalid email or password."` (same message for both).
Unverified email → `403` + the code is re-sent (never the password-check message).

## 2a. Verify email — POST `/api/verify`  ·  POST `/api/verify/resend`
`/api/verify` body: `email=..&code=######`
Wrong/expired code → `400`. Correct → `200` `"Email verified. You can now log in."`

`/api/verify/resend` body: `email=..`. Replies the same success text whether or
not the account exists (no enumeration); only sends a code if the account is
real and unverified. A resend replaces the old code.

Demo: `GET /api/dev/mailbox` returns the simulated inbox
(`{"mailbox":[{"to":"..","subject":"..","body":".."}]}`) — production `404`.

## 2b. Logout — POST `/api/logout` (Bearer token required)
Invalidates the token in the in-memory session store, so it can't be reused.
Success: `{"status":"ok","message":"Logged out"}`. No body needed.

## 2c. Update Profile — POST `/api/users/profile` (Bearer token required)
| Param | Required | Notes |
|---|---|---|
| name | no | New display name |
| password | no | New password (re-hashed, PBKDF2) |

Send at least one. Success: `{"status":"ok","userId":1,"name":"Maria","role":"SENDER"}`

## 3. Register Parcel — POST `/api/parcels`
| Param | Required | Notes |
|---|---|---|
| sender | yes | Sender name |
| receiver | yes | Receiver name |
| weight | yes | kg, number (e.g. `2.5`); bounds 0 < weight ≤ 200 |
| service | no | `regular` (default), `express`, `same-day` |
| vehicle | no | `MOTORCYCLE`, `VAN` (default), `MINIVAN`, `TRUCK` |
| paymentMethod | no | `COD` (default) or `ONLINE` |
| updatedBy | no | Who registered it (default `branch_staff`) |

Every parcel gets a **public tracking code** (format `LAYAG-XXXX-XXXX-XXXX`)
generated at registration — this is the number you give the sender. The internal
`parcelId` is for system use; customers track with the code.
Booking also creates the parcel's **payment transaction** (one `payments` row,
amount = fee, status PENDING). The logged-in account is recorded as the parcel
**owner** (`created_by_user_id`) — payment and receipt endpoints then require
that owner (or an admin).

Success: `{"status":"ok","parcelId":14,"trackingCode":"LAYAG-8BXF-JZJR-7HHS","fee":108.0,"currentStatus":"REGISTERED","vehicle":"TRUCK","paymentMethod":"COD"}`

## 4. Track Parcel — GET `/api/parcels?id=12` (system)  or  `?code=LAYAG-XXXX-XXXX-XXXX` (public)
Success: `{"status":"ok","parcelId":12,"trackingCode":"LAYAG-...","sender":"Maria","receiver":"Pedro","weightKg":2.5,"fee":250.0,"currentStatus":"IN_TRANSIT","vehicle":"TRUCK"}`
Not found → `404`

## 5. Timeline — GET `/api/parcels?id=12&view=timeline`  (or `?code=LAYAG-...&view=timeline`)
Success:
```json
{"status":"ok","parcelId":12,"timeline":[
  {"status":"REGISTERED","location":"Registration","updatedAt":"2026-09-10 00:11:24","updatedBy":"branch_staff"},
  {"status":"PICKED_UP","location":"Quezon City","updatedAt":"...","updatedBy":"rider_juan"},
  {"status":"IN_TRANSIT","location":"Pasig Hub","updatedAt":"...","updatedBy":"rider_juan"}]}
```
Order is oldest → newest (append each new event to the timeline).

## 6. Update Status — POST `/api/parcels/status`
| Param | Required | Notes |
|---|---|---|
| id | yes * | Parcel ID **or** |
| code | yes * | Tracking code (`LAYAG-...`) — pass one of the two |
| status | yes | see allowed flow below |
| location | yes | Where the parcel is |
| updatedBy | no | User doing the update |

Allowed flow: `REGISTERED→PICKED_UP→IN_TRANSIT→OUT_FOR_DELIVERY→DELIVERED`
Terminal: `FAILED`, `RETURNED`. `IN_TRANSIT→IN_TRANSIT` allowed (multi-hub).
Illegal skip (e.g. `REGISTERED→DELIVERED`) → `400`.

Success: `{"status":"ok","newStatus":"OUT_FOR_DELIVERY"}`

## 7. GPS — Record Ping — POST `/api/gps`
| Param | Required |
|---|---|
| parcelId | yes |
| lat | yes (decimal, e.g. `14.5862`) |
| lng | yes (decimal, e.g. `121.1770`) |

Success: `{"status":"ok","message":"Location recorded"}`

## 8. GPS — Get Route — GET `/api/gps?parcelId=12`
Success:
```json
{"status":"ok","parcelId":12,"route":[
  {"lat":14.6324,"lng":121.0381,"recordedAt":"2026-09-10 00:22:37"},
  {"lat":14.5862,"lng":121.1770,"recordedAt":"..."}]}
```

## 9. Search Parcels — GET `/api/parcels/search`
| Param | Notes |
|---|---|
| term | matches sender OR receiver name OR tracking code (LIKE search) |
| status | optional, e.g. `DELIVERED` — filters the results |

Success (JSON array): `[{"parcelId":5,"trackingCode":"LAYAG-...","sender":"Maria","receiver":"Pedro","weightKg":3.0,"fee":180.0,"currentStatus":"PICKED_UP","vehicle":"VAN"}, ...]`

## 10. Filter by Status — GET `/api/parcels?status=DELIVERED`
Same array shape as search. Valuable for admin/list views.

---

## Payments (COD + Online)

One `payments` row per parcel, created at booking. Lifecycle:
`PENDING → COMPLETED (paid_at set) | FAILED`. **COD** completes the moment the
rider delivers (cash collected, auto-triggered on status `DELIVERED`). **ONLINE**
completes when charged through the mock gateway.

| Endpoint | Method | Auth | What it does |
|---|---|---|---|
| `/api/payments/parcel?code=..` or `?id=..` | GET | logged-in user **and parcel owner** (or ADMIN) | Payment record for a parcel |
| `/api/payments/charge` | POST | logged-in user **and parcel owner** (or ADMIN) | ONLINE: pay via mock gateway → `COMPLETED` + reference (`PMP-...`). Idempotent; COD order → `400` |
| `/api/payments/confirm-collect` | POST | COURIER/ADMIN | COD: rider confirms cash → `COMPLETED` + `COD-<id>` ref. ONLINE order → `400` |
| `/api/payments/receipt?code=..` or `?id=..` | GET | logged-in user **and parcel owner** (or ADMIN) | Itemized receipt: tier, rate, service/vehicle multipliers, surcharge, fee, payment record |
| `/api/admin/payments` | GET | ADMIN | All transactions + `totalCollected`, `totalPending`, COD/ONLINE counts (settlement view) |

`/api/payments/charge` body: `code=LAYAG-...` (or `id=..`).

Receipt example:
```json
{"status":"ok","trackingCode":"LAYAG-MYRQ-NG3S-C4CD","sender":"...","receiver":"...",
 "weightKg":4.0,"serviceType":"same-day","vehicle":"TRUCK","fee":420.0,
 "tierLabel":"3-10 kg","ratePerKg":35.0,"serviceMultiplier":2.0,"vehicleMultiplier":1.5,
 "interIslandSurcharge":0.0,
 "payment":{"paymentId":2,"parcelId":19,"amount":420.0,"method":"ONLINE","status":"COMPLETED",
            "reference":"PMP-1B2D6D2C","paidAt":"2026-09-13 12:56:59","createdAt":"2026-09-13 12:56:59"}}
```

**Honesty note (defense):** the gateway is simulated. `MockPayGateway.charge()`
returns a reference itself; in production that body becomes an HTTP call to
PayMongo/Maya and their callback updates the payment. Stored `Parcel.fee` now
**always equals** the itemized receipt total (base includes service×vehicle
multipliers; a derived surcharge line covers inter-island).

## 11. Admin Dashboard — GET `/api/admin/summary`
Success:
```json
{"status":"ok","totalParcels":13,"totalRevenue":1495.0,"averageFee":115.0,
 "statusCounts":{"IN_TRANSIT":4,"REGISTERED":3,"PICKED_UP":1,"DELIVERED":5},
 "recentParcels":[{"parcelId":13,"trackingCode":"LAYAG-...","sender":"Maria","receiver":"Pedro","fee":225.0,"status":"REGISTERED","vehicle":"TRUCK"}, ...]}
```

---

## Frontend example (JavaScript)
Login first, save the token, then send it on protected calls:
```js
fetch("http://localhost:8080/api/login", {
  method: "POST",
  headers: {"Content-Type": "application/x-www-form-urlencoded"},
  body: "email=you@layag.ph&password=pass123"
})
  .then(r => r.json())
  .then(d => {
    const token = d.token; // keep this
    return fetch("http://localhost:8080/api/parcels", {
      method: "POST",
      headers: {"Content-Type": "application/x-www-form-urlencoded",
                "Authorization": "Bearer " + token},
      body: "sender=Maria&receiver=Pedro&weight=3.0&service=express"
    });
  })
  .then(r => r.json())
  .then(d => console.log(d.parcelId, d.trackingCode, d.fee, d.currentStatus));
```

## Map tip for GPS
`GET /api/gps?parcelId=X` returns points in time order — draw them with [Leaflet](https://leafletjs.com) + OpenStreetMap tiles, and move a marker each time a new point arrives.

## Error shape (any endpoint)
`{"status":"error","message":"Illegal transition: REGISTERED -> DELIVERED"}`