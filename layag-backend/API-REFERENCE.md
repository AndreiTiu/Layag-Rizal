# LAYAG API Reference (Backend)

**Base URL:** `http://localhost:8080`
**Format:** requests = `key=value&key=value` (form data), responses = JSON
**CORS:** whitelist only — default origin `http://localhost:8081`; configure `cors.allowedOrigins` in `config.properties`. `file://` pages (Origin `null`) are allowed in development only. Unknown origins get `403` and no CORS headers.
**To start backend:** `java -cp "out;lib\mysql-connector-j-9.7.0.jar" api.ApiServer`
**Uploaded files:** proof-of-delivery photos are stored under `proofs/` in the run directory.

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
Roles: `SENDER`, `COURIER`, `ADMIN`. **Public sign-up is always SENDER** — riders
and admins are provisioned by an admin (see `/api/admin/riders`), never self-serve.

| Endpoint | Protection |
|---|---|
| `POST /api/register` | public (role always forced to `SENDER`) |
| `POST /api/login` | public (unverified accounts → `403`) |
| `POST /api/verify` / `POST /api/verify/resend` | public |
| `POST /api/logout` | logged-in user (invalidates token server-side) |
| `GET /api/dev/mailbox` | dev only (simulated email inbox; `404` in production) |
| `GET /api/parcels?...` (track by `id`/`code`, timeline) | public — customers track without logging in |
| `POST /api/parcels` | any logged-in user (SENDER) |
| `GET /api/my-parcels` | logged-in user — that user's parcels only |
| `POST /api/parcels/confirm` | parcel owner (or ADMIN) |
| `POST /api/parcels/edit` | parcel owner (or ADMIN), while REGISTERED |
| `POST /api/parcels/reschedule` | parcel owner (or ADMIN), while REGISTERED |
| `POST /api/parcels/cancel` | parcel owner (or ADMIN) — not yet DELIVERED/FAILED |
| `POST /api/parcels/status` | the **assigned rider** or `ADMIN` |
| `POST /api/payments/charge` | logged-in user **and parcel owner** (or `ADMIN`) |
| `POST /api/payments/confirm-collect` | `COURIER` or `ADMIN` |
| `GET /api/payments/parcel` | logged-in user **and parcel owner** (or `ADMIN`) |
| `GET /api/payments/receipt` | logged-in user **and parcel owner** (or `ADMIN`) |
| `GET /api/payments/my` | logged-in user — that user's payment history |
| `GET /api/admin/payments` | `ADMIN` |
| `GET /api/parcels/search` | `ADMIN` |
| `GET /api/parcels?status=...` | `ADMIN` |
| `GET /api/admin/summary` | `ADMIN` |
| `GET /api/admin/riders` | `ADMIN` (rider roster with current loads) |
| `POST /api/admin/riders` | `ADMIN` (provision a rider account) |
| `GET /api/riders/me` | `COURIER` with a rider profile |
| `POST /api/riders/availability` | assigned-rider model: `COURIER` |
| `GET /api/riders/jobs` | `COURIER` — that rider's jobs |
| `POST /api/riders/jobs/accept` | `COURIER` — accept the offered job |
| `POST /api/riders/jobs/decline` | `COURIER` — decline; system re-assigns |
| `GET /api/riders/earnings` | `COURIER` |
| `POST /api/admin/assign` | `ADMIN` (manual assignment/re-assignment) |
| `POST /api/ratings` | logged-in sender (owner of a DELIVERED parcel) |
| `GET /api/ratings/parcel` | parcel owner (or ADMIN) |
| `GET /api/ratings/rider` | any logged-in user (public average is fine) |
| `GET /api/notifications` | logged-in user — that user's inbox |
| `POST /api/notifications/read` · `/read-all` | logged-in user |
| `POST /api/proofs` | **assigned rider** (or ADMIN) |
| `GET /api/proofs?code=` | owner/admin/assigned rider |
| `GET /api/proofs/photo?code=` | owner/admin/assigned rider |
| `POST /api/users/profile` | logged-in user (edits own profile) |
| `POST/GET /api/gps` | open in demo (courier GPS page) — see note |

Missing/invalid token → `401`. Wrong role / wrong owner / not the assigned rider
→ `403`. Too many failed logins/registrations from one IP → `429` (brute-force
lockout, `security.login.maxFailures` / `security.login.lockMs`).
**`X-Forwarded-For` is ignored for rate limiting** — the socket IP is used.
Login failure replies are deliberately generic (`"Invalid email or password."`).

---

## 1. Register — POST `/api/register`
| Param | Required | Notes |
|---|---|---|
| name | yes | Full name |
| email | yes | Must be unique; trimmed + lowercased before storing |
| password | yes | Min **6 chars**; stored hashed (PBKDF2-HmacSHA256; legacy SHA-256 hashes auto-upgraded on first login) |
| role | ignored | Public sign-up is **always `SENDER`** |

Every new account starts **unverified**: a 6-digit code is "sent" (10-min TTL,
5 attempts) and the account cannot log in until confirmed via `POST /api/verify`.
Success: `{"status":"ok","verified":false,"email":"..","message":"User registered. A 6-digit verification code was sent to ..."}`
Duplicate email → `400` `"Email already registered."` (DB exception caught, no leak).

## 2. Login — POST `/api/login`
`email`, `password`. Success: `{"status":"ok","userId":4,"name":"Maria","role":"SENDER","token":"a59eca091de8420583075cf90102a62d"}`
Wrong email/password → `401` (same message). Unverified → `403` + code re-sent.

## 2a. Verify email — POST `/api/verify` · `/api/verify/resend`
`email=..&code=######`; wrong/expired → `400`. Resend never reveals whether the
email exists. `GET /api/dev/mailbox` = simulated inbox (404 with a real mailer).

## 2b. Logout — POST `/api/logout`
Invalidates the token in the in-memory session store. `{"status":"ok","message":"Logged out"}`.

## 2c. Update Profile — POST `/api/users/profile`
`name` and/or `password` (re-hashed PBKDF2). `{"status":"ok","userId":4,"name":"..","role":"SENDER"}`.

---

## 3. Register Parcel — POST `/api/parcels`
| Param | Required | Notes |
|---|---|---|
| sender | yes | Sender name |
| receiver | yes | Receiver name |
| weight | yes | kg (0 < weight ≤ 200) |
| service | no | `regular` (1.0), `express` (1.5), `same-day` (2.0) |
| vehicle | no | `MOTORCYCLE` (0.8), `VAN` (1.0), `MINIVAN` (1.2), `TRUCK` (1.5) |
| pickupAddress | no | Where the parcel is picked up (stored + used for distance) |
| dropoffAddress | no | Destination (stored + used for distance) |
| distanceKm | no | Explicit distance — overrides the Haversine estimate |
| interIsland | no | `true` adds the inter-island surcharge (₱100) |
| paymentMethod | no | `CASH`, `CARD`, `EWALLET` (legacy `COD`→`CASH`, `ONLINE`→`CARD`) |
| updatedBy | no | Who registered it |

Every parcel gets a public tracking code `LAYAG-XXXX-XXXX-XXXX`, one `payments`
row (amount = fee, PENDING), and is owned by the booking account
(`created_by_user_id`) — payment/receipt/rating endpoints require that owner.
**Team/partner fields** (`courier_id`) may be passed for integration but do NOT
pick the rider — riders are auto-assigned from the live roster on confirm.

Fee formula (mirrors the itemized receipt):
`fee = round2(weight × tierRate × serviceMult × vehicleMult) + distanceKm × 15 + (interIsland ? 100 : 0)`
Distance = explicit km, else Haversine straight-line × 1.25 road factor from
pickup→dropoff (`GpsService.distanceKm`). Tier rates: 0–1kg ₱50, 1–3kg ₱40,
3–10kg ₱35, >10kg ₱30.

Success: `{"status":"ok","parcelId":42,"trackingCode":"LAYAG-QV5U-JSFP-7VEH","fee":1910.0,"currentStatus":"REGISTERED","vehicle":"VAN","paymentMethod":"CASH","pickupAddress":"..","dropoffAddress":"..","distanceKm":106.0,"interIsland":true}`

## 3a. My Parcels — GET `/api/my-parcels`
Returns the caller's own parcels (owner-scoped — never another user's).
`[{"parcelId":42,"trackingCode":"..","fee":1910.0,"currentStatus":"REGISTERED","confirmed":false,"assigned":false,...}]`

## 4. Track Parcel — GET `/api/parcels?id=12` or `?code=LAYAG-...`
Includes `pickupAddress`, `dropoffAddress`, `distanceKm`, `interIsland`,
`confirmed`. Not found → `404`. Public.

## 5. Timeline — GET `/api/parcels?id=12&view=timeline` (or `?code=..`)
Oldest → newest. Beyond the status moves (REGISTERED → PICKED_UP → IN_TRANSIT →
OUT_FOR_DELIVERY → DELIVERED) the timeline records **lifecycle events** that do
NOT change the parcel's current status:
`CONFIRMED`, `PARCEL_EDITED`, `RESCHEDULED`, `ASSIGNED`, `RIDER_ACCEPTED`,
`RIDER_DECLINED`. This is how the audit trail explains *why* a rider appeared.

## 6. Update Status — POST `/api/parcels/status`
| Param | Required | Notes |
|---|---|---|
| id / code | yes (one) | Parcel ID or tracking code |
| status | yes | allowed flow below |
| location | yes | Where the parcel is |
| updatedBy | no | User doing the update |

**Gated: only the rider currently assigned to the parcel, or an ADMIN, may move
status** — any other COURIER gets `403` (the work queue model). Allowed flow:
`REGISTERED→PICKED_UP→IN_TRANSIT→OUT_FOR_DELIVERY→DELIVERED`;
`IN_TRANSIT→IN_TRANSIT` allowed (multi-hub); terminal `FAILED`/`RETURNED`.
- Reaching **DELIVERED**: a CASH payment auto-completes (`COD-<id>` ref), the
  assignment closes, and status events notify the owner.
- Reaching a **terminal** status also closes the assignment.

## 7. Confirm — POST `/api/parcels/confirm`  (`code` or `id`)
Owner confirms the booking → parcel becomes `confirmed=true` and the system
**auto-assigns a rider** from the live roster (vehicle match + free capacity).
If none is available, the parcel stays confirmed-and-unassigned (retry on
availability change). Emits `CONFIRMED` + `ASSIGNED` events and an
`ASSIGNMENT` notification to the rider.
Response includes the picked rider:
`{"status":"ok","trackingCode":"..","confirmed":true,"assignment":{"assignmentId":7,"riderUserId":36,"vehicle":"VAN","capacityUsed":1,"maxConcurrent":3}}`

## 7a. Edit Parcel — POST `/api/parcels/edit` (`code`, one ore more fields)
Owner edits weight/service/vehicle/addresses/distanceKM while RECISTERED → the
fee **re-prices** (`payments.amount` updated, tracked in version/activity),
emits `PARCEL_EDITED`.

## 7b. Reschedule — POST `/api/parcels/reschedule` (`code`)
Owner reschedules a REGISTERED parcel (simulated date moved forward), emits `RESCHEDULED`.

## 7c. Cancel — POST `/api/parcels/cancel` (`code`)
Owner cancels before delivery finishes → `CANCELLED` terminal, frees the rider,
notifies the assigned rider, refunds nothing (no-refunds limitation).

---

## 8. Rider Administration (Phase 2)

### Provision a rider — POST `/api/admin/riders`
`name=..&email=..&password=..&vehicle=MOTORCYCLE|VAN|MINIVAN|TRUCK&maxConcurrent=..&city=..`
Creates the account (always COURIER), marks it verified, and inserts a
`rider_profiles` row. The password is meant to be issued by the admin (there is
no self-serve role escalation).

### Roster — GET `/api/admin/riders`
All riders + their current load and stats:
`[{"riderUserId":36,"name":"Anna","email":"anna2@layag.ph","vehicle":"VAN","maxConcurrent":3,"currentLoad":0,"city":"Cainta","available":true,"averageRating":5.0,"ratingCount":2},...]`

### Auto-assignment (engine)
On confirm, `AssignmentService.autoAssign` scores candidates who are
`available` and have free capacity:
`score = VEHICLE_MATCH_BONUS(10 if exact vehicle) + FREE_CAPACITY_WEIGHT(3 × free slots)`
Best score wins; ties broken by highest free capacity, then earliest id. The
rider is notified and the job appears in their queue.

### Manual assign / re-assign — POST `/api/admin/assign`
`parcelId/code` + `riderUserId`. Re-assign moves the existing assignment row
(parcel_id stays unique — no double-booking).

## 9. Rider app (COURIER role)

| Endpoint | Body | What it does |
|---|---|---|
| `GET /api/riders/me` | — | own profile + load + rating avg |
| `POST /api/riders/availability` | `available=true/false` | toggle 'accepting jobs' (unavailable riders are not auto-assigned) |
| `GET /api/riders/jobs` | — | jobs offered to this rider (accepted = active delivery) |
| `POST /api/riders/jobs/accept` | `assignmentId=..` | accept the job |
| `POST /api/riders/jobs/decline` | `assignmentId=..` | decline → system re-assigns to the next best rider (decliner excluded); owner notified |
| `GET /api/riders/earnings` | — | delivered-parcel earnings total |

Status gate note: after accepting, **only this rider** (or ADMIN) can move the
parcel's status — matches "the rider who carries the parcel drives its events".

---

## 10. Payments (CASH / CARD / EWALLET)

One `payments` row per parcel. `PENDING → COMPLETED | FAILED`.
- **CASH** (legacy COD): auto-COMPLETED the moment the parcel is DELIVERED.
- **CARD / EWALLET** (legacy ONLINE): COMPLETED via `POST /api/payments/charge`
  (MockPayGateway; idempotent — re-charge returns the same reference).

| Endpoint | Method | Auth | What it does |
|---|---|---|---|
| `/api/payments/parcel?code=..`·`?id=..` | GET | owner/admin | payment record for a parcel |
| `/api/payments/charge` | POST | owner/admin | CARD/EWALLET → `COMPLETED` + `PMP-...` ref; CASH order → `400` |
| `/api/payments/confirm-collect` | POST | COURIER/ADMIN | CASH: confirm money collected → `COMPLETED` + `COD-<id>`; non-CASH → `400` |
| `/api/payments/receipt?code=..`·`?id=..` | GET | owner/admin | itemized receipt (tier/rate/multipliers + distance + surcharge) |
| `/api/payments/my` | GET | logged-in user | that user's payment history (owner-scoped) |
| `/api/admin/payments` | GET | ADMIN | all transactions + `totalCollected` + `codCount`, `onlineCount`, `ewalletCount`, `collected` |

`paymentMethod` is stored normalized (`CASH`/`CARD`/`EWALLET`); legacy rows with
`COD`/`ONLINE` are still understood by `normalizePaymentMethod` (used by charge,
receipt, settlement counts). `EWALLET` is treated as a card-style online method.

Receipt example (now distance-aware):
```json
{"status":"ok","trackingCode":"LAYAG-...","sender":"..","receiver":"..",
 "weightKg":5.0,"serviceType":"express","vehicle":"VAN","fee":2110.0,
 "tierLabel":"3-10 kg","ratePerKg":35.0,"serviceMultiplier":1.5,"vehicleMultiplier":1.0,
 "interIslandSurcharge":100.0,"distanceKm":120.0,"distanceFee":1800.0,
 "payment":{"paymentId":..,"method":"CASH","status":"COMPLETED","reference":"CASH-..","...":..}}
```
Ferry/distance honesty: `distanceKm × 15` is a variable per-km charge in
addition to the weight×rate model; inter-island adds the flat surcharge.

---

## 11. Ratings & reviews (Phase 4)

| Endpoint | Method | Auth | Body / Query | What it does |
|---|---|---|---|---|
| `/api/ratings` | POST | parcel owner | `code`/`id`, `stars` (1–5), `comment?` (≤500) | rate a DELIVERED parcel; one rating per parcel (re-rate → `400`) |
| `/api/ratings/parcel` | GET | owner/admin | `code`/`id` | the parcel's rating |
| `/api/ratings/rider` | GET | any login | `riderId` | rider's `average` + `count` + list |

Rider average appears on `/api/admin/riders` and `/api/riders/me`.

## 12. Notifications (Phase 5)

| Endpoint | Method | Auth | What it does |
|---|---|---|---|
| `/api/notifications` | GET | user | that user's inbox (newest first; `unread` count) |
| `/api/notifications/read` | POST | user | `notificationId=..` → mark read |
| `/api/notifications/read-all` | POST | user | mark all read |

Event hooks automatically notify the right people:
- status moves / delivery → **owner** (`PARCEL_STATUS` / `PAYMENT`)
- auto-assign & accept → **rider** / **owner** (`ASSIGNMENT`)
- decline/re-assign → next rider
- payment charge / cash collect → owner
- cancel → assigned rider

## 13. Proof of delivery (Phase 6)

| Endpoint | Method | Auth | Body / Query | What it does |
|---|---|---|---|---|
| `/api/proofs` | POST | **assigned rider**/ADMIN | `code`/`id`, `photo` (base64 **or** `data:image/...;base64,`), `notes?`, `recipientName?` | must be OUT_FOR_DELIVERY or DELIVERED; max 3 MB; saves file `proofs/proof_<parcelId>.<ext>` + one DB row (re-upload overwrites) |
| `/api/proofs?code=..` | GET | owner/admin/rider | — | proof metadata (photo path, notes, recipient, delivered_at) |
| `/api/proofs/photo?code=..` | GET | owner/admin/rider | — | the image bytes with correct `Content-Type` |

Access rule: the sender (owner), the assigned rider, and admins may read a
proof; anyone else → `403`.

---

## 14. Admin Dashboard — GET `/api/admin/summary`
`totalParcels`, `totalRevenue`, `averageFee`, `statusCounts`, `recentParcels`.
(Also `GET /api/parcels/search?term=..&status=..` and `GET /api/parcels?status=..`.)
`GET /api/admin/payments` = settlement view (see §10).

## 15. GPS — POST `/api/gps` (`parcelId,lat,lng`) · GET `/api/gps?parcelId=X`
Open for the demo map. Points in time order; draw with Leaflet + OSM.

---

## Frontend example (JavaScript)
```js
fetch("http://localhost:8080/api/login", {
  method: "POST",
  headers: {"Content-Type": "application/x-www-form-urlencoded"},
  body: "email=you@layag.ph&password=pass123"
}).then(r => r.json()).then(d => {
  const token = d.token;
  return fetch("http://localhost:8080/api/parcels", {
    method: "POST",
    headers: {"Content-Type": "application/x-www-form-urlencoded",
              "Authorization": "Bearer " + token},
    body: "sender=Maria&receiver=Pedro&weight=3.0&service=express&vehicle=VAN&distanceKm=10&paymentMethod=CASH"
  });
}).then(r => r.json()).then(d => console.log(d.trackingCode, d.fee));
```

## Error shape (any endpoint)
`{"status":"error","message":"Illegal transition: REGISTERED -> DELIVERED"}`