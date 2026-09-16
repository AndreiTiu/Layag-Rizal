package api;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import config.Config;
import model.Parcel;
import model.StatusEvent;
import model.User;
import model.LocationPoint;
import model.DashboardSummary;
import model.AuthSession;
import model.Payment;
import model.FeeBreakdown;
import model.ParcelAssignment;
import model.RiderProfile;
import model.Rating;
import model.Notification;
import model.DeliveryProof;
import mail.Mailer;
import mail.DemoMailer;
import mail.ResendMailer;
import repository.*;
import service.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.Base64;

public class ApiServer {
    private final ParcelService parcelService;
    private final TrackingService trackingService;
    private final AuthService authService;
    private final GpsService gpsService;
    private final ReportService reportService;
    private final AuthSessionService sessionService;
    private final PaymentService paymentService;
    private final FeeService feeService;
    private final AssignmentService assignmentService;
    private final RatingService ratingService;
    private final NotificationService notificationService;
    private final DeliveryProofService deliveryProofService;
    private final UserRepository userRepo;
    private final Mailer mailer;
    private final EmailVerificationService emailVerificationService;

    // Login/register lockout: max consecutive failures per IP, then blocked.
    private static final int LOGIN_MAX_FAIL = Config.getInt("security.login.maxFailures", "LAYAG_LOGIN_MAX_FAIL", 5);
    private static final long LOGIN_LOCK_MS = Config.getInt("security.login.lockMs", "LAYAG_LOGIN_LOCK_MS", 15 * 60 * 1000);
    private static final int REGISTER_MAX_FAIL = Config.getInt("security.register.maxFailures", "LAYAG_REGISTER_MAX_FAIL", 10);

    // Origins allowed to call this API from a browser. No wildcard: a page from
    // an unlisted site must not be able to issue authenticated requests.
    private static final String[] CORS_WHITELIST =
        Config.get("cors.allowedOrigins", "LAYAG_CORS_ORIGINS", "http://localhost:8081").split(",");
    // Pages opened straight from disk send Origin: null. Development convenience only.
    private static final boolean ALLOW_FILE_ORIGIN = !Config.production();

    // Client IP -> consecutive failed auth attempts (lockout state).
    private final ConcurrentHashMap<String, AttemptRecord> ipAttempts = new ConcurrentHashMap<>();

    private static final class AttemptRecord {
        int failures;
        long lockedUntil;
    }

    public ApiServer() {
        ParcelRepository parcelRepo = new ParcelRepositoryJdbc();
        StatusHistoryRepository historyRepo = new StatusHistoryRepositoryJdbc();
        UserRepository userRepo = new UserRepositoryJdbc();
        GpsRepository gpsRepo = new GpsRepositoryJdbc();
        this.feeService = new FeeService();
        this.parcelService = new ParcelService(parcelRepo, feeService);
        this.trackingService = new TrackingService(historyRepo);
        this.authService = new AuthService(userRepo);
        this.gpsService = new GpsService(gpsRepo);
        this.reportService = new ReportService(parcelRepo);
        this.sessionService = new AuthSessionService();
        this.paymentService = new PaymentService(new PaymentRepositoryJdbc());
        this.assignmentService = new AssignmentService(new RiderRepositoryJdbc(), new AssignmentRepositoryJdbc(), new RatingRepositoryJdbc());
        this.ratingService = new RatingService(new RatingRepositoryJdbc());
        this.userRepo = userRepo;
        // Real email OTP when a Resend API key is configured; otherwise fall
        // back to the simulated DemoMailer so the project still runs offline.
        String mailKey = Config.get("mail.resendApiKey", "LAYAG_MAIL_RESEND_APIKEY", "");
        if (mailKey.isEmpty()) {
            this.mailer = new DemoMailer();
        } else {
            this.mailer = new ResendMailer(mailKey,
                Config.get("mail.from", "LAYAG_MAIL_FROM", "LAYAG <onboarding@resend.dev>"));
        }
        this.emailVerificationService = new EmailVerificationService(userRepo, this.mailer);
        this.notificationService = new NotificationService(new NotificationRepositoryJdbc(), this.mailer);
        this.deliveryProofService = new DeliveryProofService(new DeliveryProofRepositoryJdbc());
    }

    public static void main(String[] args) throws IOException {
        ApiServer api = new ApiServer();
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        // Bounded thread pool: only http.threads requests are handled at once.
        // Without a limit the server spawns a thread per connection and a few
        // slow idle clients could exhaust memory (denial of service).
        server.setExecutor(Executors.newFixedThreadPool(
            Config.getInt("http.threads", "LAYAG_HTTP_THREADS", 10)));

        // Every route passes through corsGuard() first (whitelist + OPTIONS
        // preflight), then the real handler runs.
        server.createContext("/api/register", ex -> { if (api.corsGuard(ex)) return; api.handleRegister(ex); });
        server.createContext("/api/login", ex -> { if (api.corsGuard(ex)) return; api.handleLogin(ex); });
        server.createContext("/api/parcels", ex -> { if (api.corsGuard(ex)) return; api.handleParcels(ex); });
        server.createContext("/api/parcels/my", ex -> { if (api.corsGuard(ex)) return; api.handleMyParcels(ex); });
        server.createContext("/api/my-parcels", ex -> { if (api.corsGuard(ex)) return; api.handleMyParcels(ex); });
        server.createContext("/api/parcels/status", ex -> { if (api.corsGuard(ex)) return; api.handleStatus(ex); });
        server.createContext("/api/parcels/cancel", ex -> { if (api.corsGuard(ex)) return; api.handleCancel(ex); });
        server.createContext("/api/parcels/edit", ex -> { if (api.corsGuard(ex)) return; api.handleEdit(ex); });
        server.createContext("/api/parcels/confirm", ex -> { if (api.corsGuard(ex)) return; api.handleConfirmParcel(ex); });
        server.createContext("/api/parcels/reschedule", ex -> { if (api.corsGuard(ex)) return; api.handleReschedule(ex); });
        server.createContext("/api/gps", ex -> { if (api.corsGuard(ex)) return; api.handleGps(ex); });
        server.createContext("/api/admin/summary", ex -> { if (api.corsGuard(ex)) return; api.handleAdminSummary(ex); });
        server.createContext("/api/users/profile", ex -> { if (api.corsGuard(ex)) return; api.handleProfile(ex); });
        server.createContext("/api/payments/charge", ex -> { if (api.corsGuard(ex)) return; api.handlePaymentCharge(ex); });
        server.createContext("/api/payments/confirm-collect", ex -> { if (api.corsGuard(ex)) return; api.handlePaymentCollect(ex); });
        server.createContext("/api/payments/parcel", ex -> { if (api.corsGuard(ex)) return; api.handlePaymentByParcel(ex); });
        server.createContext("/api/payments/receipt", ex -> { if (api.corsGuard(ex)) return; api.handlePaymentReceipt(ex); });
        server.createContext("/api/payments/my", ex -> { if (api.corsGuard(ex)) return; api.handleMyPayments(ex); });
        server.createContext("/api/admin/payments", ex -> { if (api.corsGuard(ex)) return; api.handleAdminPayments(ex); });
        server.createContext("/api/admin/riders", ex -> { if (api.corsGuard(ex)) return; api.handleAdminRiders(ex); });
        server.createContext("/api/admin/assign", ex -> { if (api.corsGuard(ex)) return; api.handleAdminAssign(ex); });
        server.createContext("/api/riders/me", ex -> { if (api.corsGuard(ex)) return; api.handleRiderMe(ex); });
        server.createContext("/api/riders/availability", ex -> { if (api.corsGuard(ex)) return; api.handleRiderAvailability(ex); });
        server.createContext("/api/riders/jobs/accept", ex -> { if (api.corsGuard(ex)) return; api.handleRiderAccept(ex); });
        server.createContext("/api/riders/jobs/decline", ex -> { if (api.corsGuard(ex)) return; api.handleRiderDecline(ex); });
        server.createContext("/api/riders/jobs", ex -> { if (api.corsGuard(ex)) return; api.handleRiderJobs(ex); });
        server.createContext("/api/riders/earnings", ex -> { if (api.corsGuard(ex)) return; api.handleRiderEarnings(ex); });
        server.createContext("/api/ratings", ex -> { if (api.corsGuard(ex)) return; api.handleRating(ex); });
        server.createContext("/api/ratings/parcel", ex -> { if (api.corsGuard(ex)) return; api.handleRatingByParcel(ex); });
        server.createContext("/api/ratings/rider", ex -> { if (api.corsGuard(ex)) return; api.handleRatingByRider(ex); });
        server.createContext("/api/notifications", ex -> { if (api.corsGuard(ex)) return; api.handleNotifications(ex); });
        server.createContext("/api/notifications/read", ex -> { if (api.corsGuard(ex)) return; api.handleNotificationRead(ex); });
        server.createContext("/api/notifications/read-all", ex -> { if (api.corsGuard(ex)) return; api.handleNotificationReadAll(ex); });
        server.createContext("/api/proofs/photo", ex -> { if (api.corsGuard(ex)) return; api.handleProofPhoto(ex); });
        server.createContext("/api/proofs", ex -> { if (api.corsGuard(ex)) return; api.handleProof(ex); });
        server.createContext("/api/verify", ex -> { if (api.corsGuard(ex)) return; api.handleVerify(ex); });
        server.createContext("/api/verify/resend", ex -> { if (api.corsGuard(ex)) return; api.handleVerifyResend(ex); });
        server.createContext("/api/logout", ex -> { if (api.corsGuard(ex)) return; api.handleLogout(ex); });
        server.createContext("/api/dev/mailbox", ex -> { if (api.corsGuard(ex)) return; api.handleDevMailbox(ex); });
        server.createContext("/tracker.html", ex -> { if (api.corsGuard(ex)) return; api.handleTrackerPage(ex); });

        server.start();
        System.out.println("LAYAG API running at http://localhost:8080");
    }

    private void handleRegister(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            send(ex, 405, JsonUtil.error("Use POST"));
            return;
        }
        String ip = clientIp(ex);
        long locked = lockRemaining(ip);
        if (locked > 0) {
            send(ex, 429, JsonUtil.error("Too many attempts from this IP. Try again in "
                + ((locked + 999) / 1000) + " seconds."));
            return;
        }
        try {
            Map<String, String> body = parseBody(ex.getRequestBody());
            User user = authService.register(
                body.get("name"),
                body.get("email"),
                body.get("password"),
                "SENDER" // public sign-up can NEVER pick a privileged role
            );
            emailVerificationService.start(user.getUserId(), user.getEmail());
            clearAttempts(ip);
            send(ex, 200, JsonUtil.obj(
                "status", JsonUtil.quote("ok"),
                "verified", "false",
                "email", JsonUtil.quote(user.getEmail()),
                "message", JsonUtil.quote("User registered. A 6-digit verification code was sent to " + user.getEmail() + ".")
            ));
        } catch (Exception e) {
            long lockedNow = registerFailure(ip, REGISTER_MAX_FAIL, LOGIN_LOCK_MS);
            if (lockedNow > 0) {
                send(ex, 429, JsonUtil.error("Too many attempts. IP locked for "
                    + ((lockedNow + 999) / 1000) + " seconds."));
                return;
            }
            send(ex, 400, JsonUtil.error(e.getMessage()));
        }
    }

    private void handleLogin(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            send(ex, 405, JsonUtil.error("Use POST"));
            return;
        }
        String ip = clientIp(ex);
        long locked = lockRemaining(ip);
        if (locked > 0) {
            send(ex, 429, JsonUtil.error("Too many failed logins. Try again in "
                + ((locked + 999) / 1000) + " seconds."));
            return;
        }
        try {
            Map<String, String> body = parseBody(ex.getRequestBody());
            User u = authService.login(body.get("email"), body.get("password"));
            clearAttempts(ip);
            AuthSession session = sessionService.createSession(u.getUserId(), u.getRole(), u.getName());
            send(ex, 200, JsonUtil.obj(
                "status", JsonUtil.quote("ok"),
                "userId", String.valueOf(u.getUserId()),
                "name", JsonUtil.quote(u.getName()),
                "role", JsonUtil.quote(u.getRole()),
                "token", JsonUtil.quote(session.getToken())
            ));
        } catch (EmailNotVerifiedException e) {
            // Correct password, but the email code was never confirmed. Re-send
            // the code and explain the next step (this is not a "failure").
            try {
                emailVerificationService.resend(e.getEmail());
            } catch (Exception resendErr) {
                // Keep the 403 below even if the mail transport hiccups.
            }
            send(ex, 403, JsonUtil.error("Email not verified. A new 6-digit code was sent to " + e.getEmail() + "."
                + (mailer.isSimulated()
                    ? " In demo mode, see the code at GET /api/dev/mailbox."
                    : " Check your inbox and enter the code at POST /api/verify.")));
        } catch (Exception e) {
            long lockedNow = registerFailure(ip, LOGIN_MAX_FAIL, LOGIN_LOCK_MS);
            if (lockedNow > 0) {
                send(ex, 429, JsonUtil.error("Too many failed attempts. IP locked for "
                    + ((lockedNow + 999) / 1000) + " seconds."));
                return;
            }
            send(ex, 401, JsonUtil.error(e.getMessage()));
        }
    }

    // Confirm the email code: POST /api/verify {email, code}
    private void handleVerify(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            send(ex, 405, JsonUtil.error("Use POST"));
            return;
        }
        try {
            Map<String, String> body = parseBody(ex.getRequestBody());
            boolean ok = emailVerificationService.verify(body.get("email"), body.get("code"));
            if (!ok) {
                send(ex, 400, JsonUtil.error("Invalid or expired verification code."));
                return;
            }
            send(ex, 200, JsonUtil.ok("Email verified. You can now log in."));
        } catch (Exception e) {
            send(ex, 400, JsonUtil.error(e.getMessage()));
        }
    }

    // Re-send the code: POST /api/verify/resend {email}
    private void handleVerifyResend(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            send(ex, 405, JsonUtil.error("Use POST"));
            return;
        }
        try {
            Map<String, String> body = parseBody(ex.getRequestBody());
            emailVerificationService.resend(body.get("email"));
            // Same reply whether or not the account exists (no enumeration).
            send(ex, 200, JsonUtil.ok("If an account with that email exists and is not verified, a new code was sent."));
        } catch (Exception e) {
            send(ex, 400, JsonUtil.error(e.getMessage()));
        }
    }

    // Invalidate the token server-side: POST /api/logout (Bearer token required)
    private void handleLogout(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            send(ex, 405, JsonUtil.error("Use POST"));
            return;
        }
        if (requireSession(ex) == null) {
            return;
        }
        sessionService.logout(bearerToken(ex));
        send(ex, 200, JsonUtil.ok("Logged out"));
    }

    // Demo helper: shows the simulated mail inbox (disabled in production AND
    // when a real mailer is configured — no inbox to scrape).
    private void handleDevMailbox(HttpExchange ex) throws IOException {
        if (Config.production() || !mailer.isSimulated()) {
            send(ex, 404, JsonUtil.error("Simulated mailbox is not available."));
            return;
        }
        StringBuilder arr = new StringBuilder("[");
        int i = 0;
        for (DemoMailer.Message m : DemoMailer.inbox) {
            if (i++ > 0) {
                arr.append(",");
            }
            arr.append(JsonUtil.obj(
                "to", JsonUtil.quote(m.getTo()),
                "subject", JsonUtil.quote(m.getSubject()),
                "body", JsonUtil.quote(m.getBody())
            ));
        }
        arr.append("]");
        send(ex, 200, "{\"status\":\"ok\",\"simulated\":true,\"mailbox\":" + arr + "}");
    }

    // Logged-in user edits own profile: POST /api/users/profile
    // Body: name (optional) and/or password (optional, re-hashed).
    private void handleProfile(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            send(ex, 405, JsonUtil.error("Use POST"));
            return;
        }
        AuthSession session = requireSession(ex);
        if (session == null) {
            return;
        }
        try {
            Map<String, String> body = parseBody(ex.getRequestBody());
            boolean hasName = body.get("name") != null && !body.get("name").trim().isEmpty();
            boolean hasPassword = body.get("password") != null && !body.get("password").isEmpty();
            if (!hasName && !hasPassword) {
                send(ex, 400, JsonUtil.error("Nothing to update. Send 'name' and/or 'password'."));
                return;
            }
            User updated = authService.updateProfile(session.getUserId(), body.get("name"), body.get("password"));
            send(ex, 200, JsonUtil.obj(
                "status", JsonUtil.quote("ok"),
                "userId", String.valueOf(updated.getUserId()),
                "name", JsonUtil.quote(updated.getName()),
                "role", JsonUtil.quote(updated.getRole())
            ));
        } catch (Exception e) {
            send(ex, 400, JsonUtil.error(e.getMessage()));
        }
    }

    private void handleParcels(HttpExchange ex) throws IOException {
        if ("POST".equals(ex.getRequestMethod())) {
            // Any authenticated user may create a delivery (no token = 401).
            AuthSession session = requireSession(ex);
            if (session == null) {
                return;
            }
            Map<String, String> body = parseBody(ex.getRequestBody());
            try {
                double weight = Double.parseDouble(body.get("weight"));
                boolean interIsland = "true".equalsIgnoreCase(body.getOrDefault("interIsland", "false"));
                double distance = resolveDistance(body);
                Parcel p = parcelService.registerParcel(
                    body.get("sender"),
                    body.get("receiver"),
                    weight,
                    body.get("service") != null ? body.get("service") : "regular",
                    body.get("vehicle") != null ? body.get("vehicle") : "VAN",
                    interIsland,
                    session.getUserId(),
                    body.get("pickupAddress"),
                    body.get("dropoffAddress"),
                    distance
                );
                trackingService.recordInitialStatus(p.getParcelId(), body.getOrDefault("updatedBy", "branch_staff"));
                // One transaction per parcel: COD (default) or ONLINE (mock gateway).
                String method = normalizePaymentMethod(body.getOrDefault("paymentMethod", "COD"));
                paymentService.createForParcel(p.getParcelId(), p.getFee(), method);
                send(ex, 200, JsonUtil.obj(
                    "status", JsonUtil.quote("ok"),
                    "parcelId", String.valueOf(p.getParcelId()),
                    "trackingCode", JsonUtil.quote(p.getTrackingCode()),
                    "fee", String.valueOf(p.getFee()),
                    "currentStatus", JsonUtil.quote(p.getCurrentStatus()),
                    "vehicle", JsonUtil.quote(p.getVehicleType()),
                    "pickupAddress", JsonUtil.quote(p.getPickUpAddress()),
                    "dropoffAddress", JsonUtil.quote(p.getDropOffAddress()),
                    "distanceKm", String.valueOf(p.getDistanceKm()),
                    "interIsland", String.valueOf(interIsland),
                    "paymentMethod", JsonUtil.quote(method)
                ));
            } catch (Exception e) {
                send(ex, 400, JsonUtil.error(e.getMessage()));
            }
            return;
        }

        if ("GET".equals(ex.getRequestMethod())) {
            Map<String, String> q = parseQuery(ex.getRequestURI().getRawQuery());

            // Search / filter mode: GET /api/parcels/search?term=..&status=..
            String path = ex.getRequestURI().getPath();
            if (path.endsWith("/search")) {
                if (requireRole(ex, "ADMIN") == null) {
                    return;
                }
                ArrayList<Parcel> found = parcelService.searchParcels(q.get("term"));
                String status = q.get("status");
                if (status != null && !status.isEmpty()) {
                    found = filterByStatus(found, status);
                }
                send(ex, 200, parcelsJson(found));
                return;
            }

            // Filter mode: GET /api/parcels?status=IN_TRANSIT (no id) - admin tool
            if (q.get("id") == null && q.get("status") != null) {
                if (requireRole(ex, "ADMIN") == null) {
                    return;
                }
                send(ex, 200, parcelsJson(parcelService.parcelsByStatus(q.get("status"))));
                return;
            }

            // Lookup either by internal id OR public tracking code.
            Parcel p;
            if (q.get("code") != null) {
                p = parcelService.trackByCode(q.get("code").trim());
            } else {
                int id = Integer.parseInt(q.get("id"));
                p = parcelService.trackParcel(id);
            }
            if ("timeline".equals(q.get("view"))) {
                if (p == null) {
                    send(ex, 404, JsonUtil.error("Parcel not found"));
                    return;
                }
                int id = p.getParcelId();
                ArrayList<StatusEvent> timeline = trackingService.getTimeline(id);
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < timeline.size(); i++) {
                    StatusEvent ev = timeline.get(i);
                    if (i > 0) {
                        sb.append(",");
                    }
                    sb.append(JsonUtil.obj(
                        "status", JsonUtil.quote(ev.getStatus()),
                        "location", JsonUtil.quote(ev.getLocation()),
                        "updatedAt", JsonUtil.quote(ev.getTimestamp()),
                        "updatedBy", JsonUtil.quote(ev.getUpdatedBy())
                    ));
                }
                sb.append("]");
                send(ex, 200, "{\"status\":\"ok\",\"parcelId\":" + id + ",\"trackingCode\":" + JsonUtil.quote(p.getTrackingCode()) + ",\"timeline\":" + sb + "}");
            } else {
                if (p == null) {
                    send(ex, 404, JsonUtil.error("Parcel not found"));
                    return;
                }
                send(ex, 200, JsonUtil.obj(
                    "status", JsonUtil.quote("ok"),
                    "parcelId", String.valueOf(p.getParcelId()),
                    "trackingCode", JsonUtil.quote(p.getTrackingCode()),
                    "sender", JsonUtil.quote(p.getSenderName()),
                    "receiver", JsonUtil.quote(p.getReceiverName()),
                    "weightKg", String.valueOf(p.getWeightKg()),
                    "fee", String.valueOf(p.getFee()),
                    "currentStatus", JsonUtil.quote(p.getCurrentStatus()),
                    "vehicle", JsonUtil.quote(p.getVehicleType()),
                    "pickupAddress", JsonUtil.quote(p.getPickUpAddress()),
                    "dropoffAddress", JsonUtil.quote(p.getDropOffAddress()),
                    "distanceKm", String.valueOf(p.getDistanceKm()),
                    "interIsland", String.valueOf(p.isInterIsland()),
                    "confirmed", String.valueOf(p.isConfirmed())
                ));
            }
        }
    }

    private void handleStatus(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            send(ex, 405, JsonUtil.error("Use POST"));
            return;
        }
        // Only couriers and admins may move a parcel's status.
        AuthSession session = requireRole(ex, "COURIER", "ADMIN");
        if (session == null) {
            return;
        }
        try {
            Map<String, String> body = parseBody(ex.getRequestBody());
            String toStatus = body.get("status");
            String location = body.get("location");
            String updatedBy = body.get("updatedBy");

            // Resolve target either by id or by tracking code.
            Parcel p;
            if (body.get("code") != null) {
                p = parcelService.trackByCode(body.get("code").trim());
            } else {
                p = parcelService.trackParcel(Integer.parseInt(body.get("id")));
            }
            if (p == null) {
                send(ex, 404, JsonUtil.error("Parcel not found"));
                return;
            }

            // Assignment gate: once a rider owns a job, only THAT rider (or an
            // admin) may move the parcel further - nobody else impersonates the
            // driver on an order.
            if (!"ADMIN".equals(session.getRole())
                    && !assignmentService.isAssignedTo(p.getParcelId(), session.getUserId())) {
                send(ex, 403, JsonUtil.error("Forbidden: update deliveries assigned to you."));
                return;
            }

            trackingService.updateStatus(p.getParcelId(), toStatus, location, updatedBy, p.getCurrentStatus());
            parcelService.updateStatus(p.getParcelId(), toStatus);
            // Cash on delivery: payment completes the moment cash is collected.
            if ("DELIVERED".equals(toStatus)) {
                Payment pmt = paymentService.getByParcel(p.getParcelId());
                if (pmt != null && ("CASH".equals(pmt.getMethod()) || "COD".equals(pmt.getMethod()))) {
                    paymentService.confirmCollected(p.getParcelId());
                }
            }
            // Terminal states close the rider's slot (and count as earnings).
            if ("DELIVERED".equals(toStatus) || "FAILED".equals(toStatus)
                    || "RETURNED".equals(toStatus) || "CANCELLED".equals(toStatus)) {
                assignmentService.closeForParcel(p.getParcelId(), toStatus);
            }
            if (p.getCreatedByUserId() > 0) {
                notify(p.getCreatedByUserId(), "PARCEL_STATUS",
                    "Delivery update: " + p.getTrackingCode(),
                    "Your parcel " + p.getTrackingCode() + " is now " + toStatus.replace('_', ' ') + ".");
            }
            send(ex, 200, JsonUtil.obj(
                "status", JsonUtil.quote("ok"),
                "parcelId", String.valueOf(p.getParcelId()),
                "trackingCode", JsonUtil.quote(p.getTrackingCode()),
                "newStatus", JsonUtil.quote(toStatus)
            ));
        } catch (Exception e) {
            send(ex, 400, JsonUtil.error(e.getMessage()));
        }
    }

    // Sender's own bookings: GET /api/parcels/my
    private void handleMyParcels(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) {
            send(ex, 405, JsonUtil.error("Use GET"));
            return;
        }
        AuthSession session = requireSession(ex);
        if (session == null) {
            return;
        }
        send(ex, 200, "{\"status\":\"ok\",\"parcels\":" + parcelsJson(parcelService.parcelsByOwner(session.getUserId())) + "}");
    }

    // Cancel before pickup: POST /api/parcels/cancel {code|id}
    private void handleCancel(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            send(ex, 405, JsonUtil.error("Use POST"));
            return;
        }
        if (requireSession(ex) == null) {
            return;
        }
        try {
            Map<String, String> body = parseBody(ex.getRequestBody());
            Parcel p = resolveParcelByBody(body);
            if (p == null) {
                send(ex, 404, JsonUtil.error("Parcel not found"));
                return;
            }
            if (requireOwner(ex, p) == null) {
                return;
            }
            if (!"REGISTERED".equals(p.getCurrentStatus())) {
                send(ex, 400, JsonUtil.error("Only a REGISTERED (not yet picked up) parcel can be cancelled."));
                return;
            }
            trackingService.updateStatus(p.getParcelId(), "CANCELLED", body.getOrDefault("location", "Customer request"), body.getOrDefault("updatedBy", "customer"), p.getCurrentStatus());
            parcelService.updateStatus(p.getParcelId(), "CANCELLED");
            assignmentService.closeForParcel(p.getParcelId(), "CANCELLED");
            ParcelAssignment freed = assignmentService.byParcel(p.getParcelId());
            if (freed != null && freed.getRiderUserId() > 0) {
                notify(freed.getRiderUserId(), "PARCEL_STATUS",
                    "Delivery cancelled",
                    "Parcel " + p.getTrackingCode() + " was cancelled by the sender.");
            }
            send(ex, 200, JsonUtil.obj("status", JsonUtil.quote("ok"), "trackingCode", JsonUtil.quote(p.getTrackingCode()), "currentStatus", JsonUtil.quote("CANCELLED")));
        } catch (Exception e) {
            send(ex, 400, JsonUtil.error(e.getMessage()));
        }
    }

    // Edit before assignment: POST /api/parcels/edit {code|id, sender?, receiver?,
    // pickupAddress?, dropoffAddress?, weight?, vehicle?, service?, interIsland?, distanceKm?}
    private void handleEdit(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            send(ex, 405, JsonUtil.error("Use POST"));
            return;
        }
        if (requireSession(ex) == null) {
            return;
        }
        try {
            Map<String, String> body = parseBody(ex.getRequestBody());
            Parcel p = resolveParcelByBody(body);
            if (p == null) {
                send(ex, 404, JsonUtil.error("Parcel not found"));
                return;
            }
            if (requireOwner(ex, p) == null) {
                return;
            }
            if (!"REGISTERED".equals(p.getCurrentStatus())) {
                send(ex, 400, JsonUtil.error("Edits are only allowed before the parcel is picked up."));
                return;
            }
            Double newWeight = body.get("weight") == null ? null : Double.valueOf(body.get("weight"));
            Double newDistance = body.get("distanceKm") == null ? null : Double.valueOf(body.get("distanceKm"));
            Boolean newIsland = body.get("interIsland") == null ? null : Boolean.valueOf(body.get("interIsland"));
            Parcel updated = parcelService.editParcel(
                p.getParcelId(),
                body.get("sender"),
                body.get("receiver"),
                body.get("pickupAddress"),
                body.get("dropoffAddress"),
                newWeight,
                body.get("service"),
                body.get("vehicle"),
                newIsland,
                newDistance
            );
            // Re-price the (unpaid) payment row so amount always matches the fee.
            paymentService.reprice(p.getParcelId(), updated.getFee());
            trackingService.recordEvent(p.getParcelId(), "PARCEL_EDITED", "Details edited before assignment", body.getOrDefault("updatedBy", "customer"));
            send(ex, 200, JsonUtil.obj(
                "status", JsonUtil.quote("ok"),
                "trackingCode", JsonUtil.quote(updated.getTrackingCode()),
                "fee", String.valueOf(updated.getFee()),
                "weightKg", String.valueOf(updated.getWeightKg()),
                "vehicle", JsonUtil.quote(updated.getVehicleType()),
                "pickupAddress", JsonUtil.quote(updated.getPickUpAddress()),
                "dropoffAddress", JsonUtil.quote(updated.getDropOffAddress()),
                "distanceKm", String.valueOf(updated.getDistanceKm())
            ));
        } catch (Exception e) {
            send(ex, 400, JsonUtil.error(e.getMessage()));
        }
    }

    // Confirm parcel details/weight: POST /api/parcels/confirm {code|id}
    private void handleConfirmParcel(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            send(ex, 405, JsonUtil.error("Use POST"));
            return;
        }
        if (requireSession(ex) == null) {
            return;
        }
        try {
            Map<String, String> body = parseBody(ex.getRequestBody());
            Parcel p = resolveParcelByBody(body);
            if (p == null) {
                send(ex, 404, JsonUtil.error("Parcel not found"));
                return;
            }
            if (requireOwner(ex, p) == null) {
                return;
            }
            if (!"REGISTERED".equals(p.getCurrentStatus())) {
                send(ex, 400, JsonUtil.error("Only a REGISTERED parcel can be confirmed."));
                return;
            }
            parcelService.confirmParcel(p.getParcelId());
            trackingService.recordEvent(p.getParcelId(), "CONFIRMED", body.getOrDefault("location", "Details verified"), body.getOrDefault("updatedBy", "customer"));
            // The moment a sender confirms the details, the system finds the
            // best available rider for this parcel (vehicle match + capacity).
            ParcelAssignment assigned = assignmentService.autoAssign(p);
            String assignJson = "null";
            if (assigned != null) {
                trackingService.recordEvent(p.getParcelId(), "ASSIGNED",
                    "Auto-assigned to rider " + assigned.getRiderUserId(), "system");
                assignJson = assignmentJson(assigned);
                notify(assigned.getRiderUserId(), "ASSIGNMENT",
                    "You have a new delivery",
                    "Parcel " + p.getTrackingCode() + " (" + p.getPickUpAddress() + " \u2192 " + p.getDropOffAddress() + ") is assigned to you.");
            }
            send(ex, 200, JsonUtil.obj(
                "status", JsonUtil.quote("ok"),
                "trackingCode", JsonUtil.quote(p.getTrackingCode()),
                "confirmed", "true",
                "assignment", assignJson
            ));
        } catch (Exception e) {
            send(ex, 400, JsonUtil.error(e.getMessage()));
        }
    }

    // Reschedule before pickup: POST /api/parcels/reschedule {code|id, note?}
    private void handleReschedule(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            send(ex, 405, JsonUtil.error("Use POST"));
            return;
        }
        if (requireSession(ex) == null) {
            return;
        }
        try {
            Map<String, String> body = parseBody(ex.getRequestBody());
            Parcel p = resolveParcelByBody(body);
            if (p == null) {
                send(ex, 404, JsonUtil.error("Parcel not found"));
                return;
            }
            if (requireOwner(ex, p) == null) {
                return;
            }
            if (!"REGISTERED".equals(p.getCurrentStatus())) {
                send(ex, 400, JsonUtil.error("A parcel already picked up cannot be rescheduled."));
                return;
            }
            trackingService.recordEvent(p.getParcelId(), "RESCHEDULED", body.getOrDefault("note", "Rescheduled by customer"), body.getOrDefault("updatedBy", "customer"));
            send(ex, 200, JsonUtil.obj("status", JsonUtil.quote("ok"), "trackingCode", JsonUtil.quote(p.getTrackingCode()), "event", JsonUtil.quote("RESCHEDULED")));
        } catch (Exception e) {
            send(ex, 400, JsonUtil.error(e.getMessage()));
        }
    }

    // ---- Payments ----
    private void handlePaymentCharge(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            send(ex, 405, JsonUtil.error("Use POST"));
            return;
        }
        if (requireSession(ex) == null) {
            return;
        }
        try {
            Map<String, String> body = parseBody(ex.getRequestBody());
            Parcel p = resolveParcelByBody(body);
            if (p == null) {
                send(ex, 404, JsonUtil.error("Parcel not found"));
                return;
            }
            if (requireOwner(ex, p) == null) {
                return;
            }
            Payment pmt = paymentService.chargeOnline(p.getParcelId());
            if ("COMPLETED".equals(pmt.getStatus()) && p.getCreatedByUserId() > 0) {
                notify(p.getCreatedByUserId(), "PAYMENT",
                    "Payment received",
                    "Payment of Php " + pmt.getAmount() + " for " + p.getTrackingCode() + " is complete (ref " + pmt.getReference() + ").");
            }
            send(ex, 200, paymentJson(pmt));
        } catch (Exception e) {
            send(ex, 400, JsonUtil.error(e.getMessage()));
        }
    }

    // COD: rider confirms cash collected -> POST /api/payments/confirm-collect
    private void handlePaymentCollect(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            send(ex, 405, JsonUtil.error("Use POST"));
            return;
        }
        if (requireRole(ex, "COURIER", "ADMIN") == null) {
            return;
        }
        try {
            Map<String, String> body = parseBody(ex.getRequestBody());
            Parcel p = resolveParcelByBody(body);
            if (p == null) {
                send(ex, 404, JsonUtil.error("Parcel not found"));
                return;
            }
            Payment pmt = paymentService.confirmCollected(p.getParcelId());
            if ("COMPLETED".equals(pmt.getStatus()) && p.getCreatedByUserId() > 0) {
                notify(p.getCreatedByUserId(), "PAYMENT",
                    "Payment received",
                    "Cash payment of Php " + pmt.getAmount() + " for " + p.getTrackingCode() + " was collected at drop-off.");
            }
            send(ex, 200, paymentJson(pmt));
        } catch (Exception e) {
            send(ex, 400, JsonUtil.error(e.getMessage()));
        }
    }

    // Payment record for one parcel: GET /api/payments/parcel?code=.. (or ?id=..)
    private void handlePaymentByParcel(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) {
            send(ex, 405, JsonUtil.error("Use GET"));
            return;
        }
        if (requireSession(ex) == null) {
            return;
        }
        try {
            Map<String, String> q = parseQuery(ex.getRequestURI().getRawQuery());
            Parcel p = resolveParcelByQuery(q);
            if (p == null) {
                send(ex, 404, JsonUtil.error("Parcel not found"));
                return;
            }
            Payment pmt = paymentService.getByParcel(p.getParcelId());
            if (pmt == null) {
                send(ex, 404, JsonUtil.error("No payment row for this parcel"));
                return;
            }
            if (requireOwner(ex, p) == null) {
                return;
            }
            send(ex, 200, paymentJson(pmt));
        } catch (Exception e) {
            send(ex, 400, JsonUtil.error(e.getMessage()));
        }
    }

    // Itemized receipt: GET /api/payments/receipt?code=.. (or ?id=..)
    private void handlePaymentReceipt(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) {
            send(ex, 405, JsonUtil.error("Use GET"));
            return;
        }
        if (requireSession(ex) == null) {
            return;
        }
        try {
            Map<String, String> q = parseQuery(ex.getRequestURI().getRawQuery());
            Parcel p = resolveParcelByQuery(q);
            if (p == null) {
                send(ex, 404, JsonUtil.error("Parcel not found"));
                return;
            }
            if (requireOwner(ex, p) == null) {
                return;
            }
            Payment pmt = paymentService.getByParcel(p.getParcelId());
            // Itemized parts rebuilt from the SAME inputs that produced the fee
            // (weight, service, vehicle, distance, inter-island), so the receipt
            // always reconciles exactly with Parcel.fee.
            FeeBreakdown bd = feeService.computeBreakdown(p.getWeightKg(), p.getServiceType(), p.getVehicleType(), p.isInterIsland(), p.getDistanceKm());
            double surcharge = p.isInterIsland() ? FeeService.INTER_ISLAND_SURCHARGE : 0.0;
            send(ex, 200, JsonUtil.obj(
                "status", JsonUtil.quote("ok"),
                "trackingCode", JsonUtil.quote(p.getTrackingCode()),
                "sender", JsonUtil.quote(p.getSenderName()),
                "receiver", JsonUtil.quote(p.getReceiverName()),
                "weightKg", String.valueOf(p.getWeightKg()),
                "serviceType", JsonUtil.quote(p.getServiceType()),
                "vehicle", JsonUtil.quote(p.getVehicleType()),
                "fee", String.valueOf(p.getFee()),
                "tierLabel", JsonUtil.quote(bd.getTierLabel()),
                "ratePerKg", String.valueOf(bd.getRatePerKg()),
                "baseFee", String.valueOf(bd.getBaseFee()),
                "distanceKm", String.valueOf(bd.getDistanceKm()),
                "distanceFee", String.valueOf(bd.getDistanceFee()),
                "serviceMultiplier", String.valueOf(bd.getMultiplier()),
                "vehicleMultiplier", String.valueOf(bd.getVehicleMultiplier()),
                "interIslandSurcharge", String.valueOf(surcharge),
                "payment", paymentJson(pmt)
            ));
        } catch (Exception e) {
            send(ex, 400, JsonUtil.error(e.getMessage()));
        }
    }

    // Admin settlement view: GET /api/admin/payments
    private void handleAdminPayments(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) {
            send(ex, 405, JsonUtil.error("Use GET"));
            return;
        }
        if (requireRole(ex, "ADMIN") == null) {
            return;
        }
        ArrayList<Payment> all = paymentService.allPayments();
        double collected = 0, pending = 0;
        int codCount = 0, onlineCount = 0, ewalletCount = 0;
        StringBuilder arr = new StringBuilder("[");
        for (int i = 0; i < all.size(); i++) {
            Payment pmt = all.get(i);
            if (i > 0) {
                arr.append(",");
            }
            arr.append(paymentJson(pmt));
            if ("COMPLETED".equals(pmt.getStatus())) {
                collected += pmt.getAmount();
            } else {
                pending += pmt.getAmount();
            }
            String m = pmt.getMethod();
            if ("EWALLET".equals(m)) {
                ewalletCount++;
            } else if ("CARD".equals(m) || "ONLINE".equals(m)) {
                onlineCount++;
            } else {
                codCount++;
            }
        }
        arr.append("]");
        send(ex, 200, JsonUtil.obj(
            "status", JsonUtil.quote("ok"),
            "totalCollected", String.valueOf(collected),
            "totalPending", String.valueOf(pending),
            "codCount", String.valueOf(codCount),
            "onlineCount", String.valueOf(onlineCount),
            "ewalletCount", String.valueOf(ewalletCount),
            "payments", arr.toString()
        ));
    }

    private Parcel resolveParcelByBody(Map<String, String> body) {
        if (body.get("code") != null) {
            return parcelService.trackByCode(body.get("code").trim());
        }
        if (body.get("id") != null) {
            return parcelService.trackParcel(Integer.parseInt(body.get("id")));
        }
        return null;
    }

    private Parcel resolveParcelByQuery(Map<String, String> q) {
        if (q.get("code") != null) {
            return parcelService.trackByCode(q.get("code").trim());
        }
        if (q.get("id") != null) {
            return parcelService.trackParcel(Integer.parseInt(q.get("id")));
        }
        return null;
    }

    // Distance for the fee: an explicit distanceKm wins; otherwise compute
    // straight-line km from pickup/drop-off coordinates via Haversine and apply
    // a 1.25 road factor (roads are longer than the straight line). Zero when
    // the client gave neither (no distance fee charged).
    private double resolveDistance(Map<String, String> body) {
        if (body.get("distanceKm") != null && !body.get("distanceKm").isEmpty()) {
            double d = Double.parseDouble(body.get("distanceKm"));
            return Math.max(0, d);
        }
        if (hasCoord(body, "pickupLat") && hasCoord(body, "pickupLng")
                && hasCoord(body, "dropLat") && hasCoord(body, "dropLng")) {
            double straight = gpsService.distanceKm(
                Double.parseDouble(body.get("pickupLat")),
                Double.parseDouble(body.get("pickupLng")),
                Double.parseDouble(body.get("dropLat")),
                Double.parseDouble(body.get("dropLng"))
            );
            return Math.round(straight * 1.25 * 100.0) / 100.0;
        }
        return 0;
    }

    private boolean hasCoord(Map<String, String> body, String key) {
        String v = body.get(key);
        return v != null && !v.isEmpty();
    }

    // Payment methods per the spec: CASH / CARD / EWALLET (legacy COD/ONLINE
    // accepted for backwards compatibility). CARD|EWALLET behave like ONLINE
    // (gateway); CASH behaves like COD (collection at drop-off).
    private String normalizePaymentMethod(String raw) {
        if (raw == null) {
            return "CASH";
        }
        switch (raw.toUpperCase()) {
            case "COD":
                return "CASH";
            case "ONLINE":
                return "CARD";
            default:
                return raw.toUpperCase();
        }
    }

    private String paymentJson(Payment pmt) {
        return JsonUtil.obj(
            "paymentId", String.valueOf(pmt.getPaymentId()),
            "parcelId", String.valueOf(pmt.getParcelId()),
            "amount", String.valueOf(pmt.getAmount()),
            "method", JsonUtil.quote(pmt.getMethod()),
            "status", JsonUtil.quote(pmt.getStatus()),
            "reference", JsonUtil.quote(pmt.getReference()),
            "paidAt", JsonUtil.quote(pmt.getPaidAt()),
            "createdAt", JsonUtil.quote(pmt.getCreatedAt())
        );
    }

    private void handleGps(HttpExchange ex) throws IOException {
        if ("POST".equals(ex.getRequestMethod())) {
            try {
                Map<String, String> body = parseBody(ex.getRequestBody());
                int parcelId = Integer.parseInt(body.get("parcelId"));
                double lat = Double.parseDouble(body.get("lat"));
                double lng = Double.parseDouble(body.get("lng"));
                gpsService.recordLocation(parcelId, lat, lng);
                send(ex, 200, JsonUtil.obj("status", JsonUtil.quote("ok"), "message", JsonUtil.quote("Location recorded")));
            } catch (Exception e) {
                send(ex, 400, JsonUtil.error(e.getMessage()));
            }
            return;
        }

        if ("GET".equals(ex.getRequestMethod())) {
            Map<String, String> q = parseQuery(ex.getRequestURI().getRawQuery());
            try {
                int parcelId = Integer.parseInt(q.get("parcelId"));
                ArrayList<LocationPoint> route = gpsService.getRoute(parcelId);
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < route.size(); i++) {
                    LocationPoint pt = route.get(i);
                    if (i > 0) {
                        sb.append(",");
                    }
                    sb.append(JsonUtil.obj(
                        "lat", String.valueOf(pt.getLatitude()),
                        "lng", String.valueOf(pt.getLongitude()),
                        "recordedAt", JsonUtil.quote(pt.getRecordedAt())
                    ));
                }
                sb.append("]");
                send(ex, 200, "{\"status\":\"ok\",\"parcelId\":" + parcelId + ",\"route\":" + sb + "}");
            } catch (Exception e) {
                send(ex, 400, JsonUtil.error(e.getMessage()));
            }
        }
    }

    // Serves the live GPS tracker page so it runs on localhost (browser
    // geolocation only works on secure contexts = localhost or HTTPS).
    private void handleTrackerPage(HttpExchange ex) throws IOException {
        try {
            byte[] html = java.nio.file.Files.readAllBytes(
                java.nio.file.Paths.get("tracker.html").toAbsolutePath());
            ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            ex.sendResponseHeaders(200, html.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(html);
            }
        } catch (Exception e) {
            send(ex, 404, JsonUtil.error("tracker.html not found in project folder"));
        }
    }

    // ---- Payments - Phase 3: per-user payment history ----

    // Sender sees the status + amount for all their bookings:
    // GET /api/payments/my
    private void handleMyPayments(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) {
            send(ex, 405, JsonUtil.error("Use GET"));
            return;
        }
        AuthSession session = requireSession(ex);
        if (session == null) {
            return;
        }
        ArrayList<Parcel> mine = parcelService.parcelsByOwner(session.getUserId());
        StringBuilder arr = new StringBuilder("[");
        boolean first = true;
        for (Parcel p : mine) {
            Payment pmt = paymentService.getByParcel(p.getParcelId());
            if (pmt == null) {
                continue;
            }
            if (!first) {
                arr.append(",");
            }
            first = false;
            arr.append(JsonUtil.obj(
                "trackingCode", JsonUtil.quote(p.getTrackingCode()),
                "sender", JsonUtil.quote(p.getSenderName()),
                "receiver", JsonUtil.quote(p.getReceiverName()),
                "fee", String.valueOf(p.getFee()),
                "payment", paymentJson(pmt)
            ));
        }
        arr.append("]");
        send(ex, 200, "{\"status\":\"ok\",\"payments\":" + arr + "}");
    }

    // ---- Rider management & assignment (Phase 2) ----
    // Admin roster: GET /api/admin/riders (list).
    // Admin provisioning: POST /api/admin/riders {name, email, password?, vehicle?, maxConcurrent?}
    private void handleAdminRiders(HttpExchange ex) throws IOException {
        if (requireRole(ex, "ADMIN") == null) {
            return;
        }
        if ("GET".equals(ex.getRequestMethod())) {
            ArrayList<RiderProfile> roster = assignmentService.roster();
            StringBuilder arr = new StringBuilder("[");
            for (int i = 0; i < roster.size(); i++) {
                RiderProfile r = roster.get(i);
                if (i > 0) {
                    arr.append(",");
                }
                User u = userRepo.findById(r.getRiderUserId());
                arr.append(JsonUtil.obj(
                    "riderUserId", String.valueOf(r.getRiderUserId()),
                    "name", JsonUtil.quote(u == null ? "" : u.getName()),
                    "email", JsonUtil.quote(u == null ? "" : u.getEmail()),
                    "vehicle", JsonUtil.quote(r.getVehicleType()),
                    "available", String.valueOf(r.isAvailable()),
                    "currentLoad", String.valueOf(r.getCurrentLoad()),
                    "maxConcurrent", String.valueOf(r.getMaxConcurrent()),
                    "averageRating", String.valueOf(r.getAverageRating()),
                    "ratingCount", String.valueOf(r.getRatingCount())
                ));
            }
            arr.append("]");
            send(ex, 200, "{\"status\":\"ok\",\"riders\":" + arr + "}");
            return;
        }
        if (!"POST".equals(ex.getRequestMethod())) {
            send(ex, 405, JsonUtil.error("Use GET or POST"));
            return;
        }
        try {
            Map<String, String> body = parseBody(ex.getRequestBody());
            String email = body.get("email");
            String password = body.get("password");
            if (password == null || password.isBlank()) {
                password = randomPassword();
            }
            // Public sign-up can never pick COURIER; provisioning goes through
            // the admin so the trusted-role model stays intact.
            User rider = authService.register(body.getOrDefault("name", "Rider"), email, password, "COURIER");
            userRepo.setVerified(rider.getUserId());
            RiderProfile profile = assignmentService.provision(rider.getUserId(),
                body.get("vehicle"), body.get("maxConcurrent") == null ? 0 : Integer.parseInt(body.get("maxConcurrent")));
            send(ex, 200, JsonUtil.obj(
                "status", JsonUtil.quote("ok"),
                "riderUserId", String.valueOf(rider.getUserId()),
                "name", JsonUtil.quote(rider.getName()),
                "email", JsonUtil.quote(rider.getEmail()),
                "vehicle", JsonUtil.quote(profile.getVehicleType()),
                "maxConcurrent", String.valueOf(profile.getMaxConcurrent()),
                "password", JsonUtil.quote(password)
            ));
        } catch (Exception e) {
            send(ex, 400, JsonUtil.error(e.getMessage()));
        }
    }

    // Manual assignment by admin: POST /api/admin/assign {code|id, riderId}
    private void handleAdminAssign(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            send(ex, 405, JsonUtil.error("Use POST"));
            return;
        }
        if (requireRole(ex, "ADMIN") == null) {
            return;
        }
        try {
            Map<String, String> body = parseBody(ex.getRequestBody());
            Parcel p = resolveParcelByBody(body);
            if (p == null) {
                send(ex, 404, JsonUtil.error("Parcel not found"));
                return;
            }
            int riderId = Integer.parseInt(body.get("riderId"));
            ParcelAssignment a = assignmentService.assign(p.getParcelId(), riderId);
            trackingService.recordEvent(p.getParcelId(), "ASSIGNED", "Assigned by admin to rider " + riderId, "admin");
            send(ex, 200, JsonUtil.obj(
                "status", JsonUtil.quote("ok"),
                "trackingCode", JsonUtil.quote(p.getTrackingCode()),
                "assignment", assignmentJson(a)
            ));
        } catch (Exception e) {
            send(ex, 400, JsonUtil.error(e.getMessage()));
        }
    }

    // Rider profile/state: GET /api/riders/me
    private void handleRiderMe(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) {
            send(ex, 405, JsonUtil.error("Use GET"));
            return;
        }
        AuthSession session = requireRole(ex, "COURIER");
        if (session == null) {
            return;
        }
        RiderProfile p = assignmentService.profile(session.getUserId());
        if (p == null) {
            send(ex, 404, JsonUtil.error("No rider profile. Ask an admin to provision you."));
            return;
        }
        send(ex, 200, JsonUtil.obj(
            "status", JsonUtil.quote("ok"),
            "name", JsonUtil.quote(session.getName()),
            "vehicle", JsonUtil.quote(p.getVehicleType()),
            "available", String.valueOf(p.isAvailable()),
            "currentLoad", String.valueOf(p.getCurrentLoad()),
            "maxConcurrent", String.valueOf(p.getMaxConcurrent()),
            "averageRating", String.valueOf(p.getAverageRating()),
            "ratingCount", String.valueOf(p.getRatingCount())
        ));
    }

    // Rider shift switch: POST /api/riders/availability {available:true|false}
    private void handleRiderAvailability(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            send(ex, 405, JsonUtil.error("Use POST"));
            return;
        }
        AuthSession session = requireRole(ex, "COURIER");
        if (session == null) {
            return;
        }
        try {
            Map<String, String> body = parseBody(ex.getRequestBody());
            boolean available = Boolean.parseBoolean(body.getOrDefault("available", "true"));
            assignmentService.setAvailability(session.getUserId(), available);
            send(ex, 200, JsonUtil.obj(
                "status", JsonUtil.quote("ok"),
                "available", String.valueOf(available)
            ));
        } catch (Exception e) {
            send(ex, 400, JsonUtil.error(e.getMessage()));
        }
    }

    // Rider work queue: GET /api/riders/jobs?status=ASSIGNED (default ASSIGNED)
    private void handleRiderJobs(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) {
            send(ex, 405, JsonUtil.error("Use GET"));
            return;
        }
        AuthSession session = requireRole(ex, "COURIER");
        if (session == null) {
            return;
        }
        Map<String, String> q = parseQuery(ex.getRequestURI().getRawQuery());
        String status = q.getOrDefault("status", "ASSIGNED");
        ArrayList<ParcelAssignment> jobs = assignmentService.riderJobs(session.getUserId(), status);
        send(ex, 200, "{\"status\":\"ok\",\"jobs\":" + jobsJson(jobs) + "}");
    }

    // Rider accepts a job: POST /api/riders/jobs/accept {assignmentId}
    private void handleRiderAccept(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            send(ex, 405, JsonUtil.error("Use POST"));
            return;
        }
        AuthSession session = requireRole(ex, "COURIER");
        if (session == null) {
            return;
        }
        try {
            Map<String, String> body = parseBody(ex.getRequestBody());
            int assignmentId = Integer.parseInt(body.get("assignmentId"));
            ParcelAssignment a = assignmentService.accept(assignmentId, session.getUserId());
            trackingService.recordEvent(a.getParcelId(), "RIDER_ACCEPTED",
                "Rider " + session.getName() + " accepted", "rider");
            Parcel ap = parcelService.trackParcel(a.getParcelId());
            if (ap != null && ap.getCreatedByUserId() > 0) {
                notify(ap.getCreatedByUserId(), "ASSIGNMENT",
                    "Your rider is on the way",
                    "Rider " + session.getName() + " accepted your parcel " + ap.getTrackingCode() + ".");
            }
            send(ex, 200, JsonUtil.obj(
                "status", JsonUtil.quote("ok"),
                "assignment", assignmentJson(a)
            ));
        } catch (Exception e) {
            send(ex, 400, JsonUtil.error(e.getMessage()));
        }
    }

    // Rider declines a job; the parcel is re-offered to the next rider:
    // POST /api/riders/jobs/decline {assignmentId, reason?}
    private void handleRiderDecline(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            send(ex, 405, JsonUtil.error("Use POST"));
            return;
        }
        AuthSession session = requireRole(ex, "COURIER");
        if (session == null) {
            return;
        }
        try {
            Map<String, String> body = parseBody(ex.getRequestBody());
            int assignmentId = Integer.parseInt(body.get("assignmentId"));
            ParcelAssignment a = assignmentService.decline(assignmentId, session.getUserId(), body.get("reason"));
            trackingService.recordEvent(a.getParcelId(), "RIDER_DECLINED",
                "Rider " + session.getName() + " declined", "rider");
            Parcel p = parcelService.trackParcel(a.getParcelId());
            ParcelAssignment next = p == null ? null : assignmentService.reassign(p, session.getUserId());
            String nextJson = "null";
            if (next != null) {
                trackingService.recordEvent(p.getParcelId(), "ASSIGNED",
                    "Re-assigned to rider " + next.getRiderUserId(), "system");
                nextJson = assignmentJson(next);
                notify(next.getRiderUserId(), "ASSIGNMENT",
                    "You have a new delivery",
                    "Parcel " + p.getTrackingCode() + " was re-assigned to you (previous rider declined).");
            }
            send(ex, 200, JsonUtil.obj(
                "status", JsonUtil.quote("ok"),
                "declined", "true",
                "nextAssignment", nextJson
            ));
        } catch (Exception e) {
            send(ex, 400, JsonUtil.error(e.getMessage()));
        }
    }

    // Rider earnings: GET /api/riders/earnings
    private void handleRiderEarnings(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) {
            send(ex, 405, JsonUtil.error("Use GET"));
            return;
        }
        AuthSession session = requireRole(ex, "COURIER");
        if (session == null) {
            return;
        }
        ArrayList<ParcelAssignment> done = assignmentService.riderJobs(session.getUserId(), "COMPLETED");
        double total = 0;
        StringBuilder arr = new StringBuilder("[");
        for (int i = 0; i < done.size(); i++) {
            ParcelAssignment a = done.get(i);
            if (i > 0) {
                arr.append(",");
            }
            Parcel p = parcelService.trackParcel(a.getParcelId());
            double fee = p == null ? 0 : p.getFee();
            total += fee;
            arr.append(JsonUtil.obj(
                "assignmentId", String.valueOf(a.getAssignmentId()),
                "trackingCode", JsonUtil.quote(p == null ? "" : p.getTrackingCode()),
                "fee", String.valueOf(fee),
                "completedAt", JsonUtil.quote(a.getCompletedAt() == null ? "" : a.getCompletedAt())
            ));
        }
        arr.append("]");
        send(ex, 200, JsonUtil.obj(
            "status", JsonUtil.quote("ok"),
            "completedDeliveries", String.valueOf(done.size()),
            "totalEarnings", String.valueOf(total),
            "jobs", arr.toString()
        ));
    }

    // ---- Notifications (Phase 5) ----

    // In-app inbox: GET /api/notifications (newest 50 + unread count)
    private void handleNotifications(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) {
            send(ex, 405, JsonUtil.error("Use GET"));
            return;
        }
        AuthSession session = requireSession(ex);
        if (session == null) {
            return;
        }
        ArrayList<Notification> list = notificationService.list(session.getUserId(), 50);
        StringBuilder arr = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                arr.append(",");
            }
            Notification n = list.get(i);
            arr.append(JsonUtil.obj(
                "notificationId", String.valueOf(n.getNotificationId()),
                "type", JsonUtil.quote(n.getType()),
                "title", JsonUtil.quote(n.getTitle()),
                "message", JsonUtil.quote(n.getMessage()),
                "isRead", String.valueOf(n.isRead()),
                "createdAt", JsonUtil.quote(n.getCreatedAt())
            ));
        }
        arr.append("]");
        send(ex, 200, JsonUtil.obj(
            "status", JsonUtil.quote("ok"),
            "unread", String.valueOf(notificationService.unreadCount(session.getUserId())),
            "notifications", arr.toString()
        ));
    }

    // Mark one as read: POST /api/notifications/read {id}
    private void handleNotificationRead(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            send(ex, 405, JsonUtil.error("Use POST"));
            return;
        }
        AuthSession session = requireSession(ex);
        if (session == null) {
            return;
        }
        try {
            Map<String, String> body = parseBody(ex.getRequestBody());
            notificationService.markRead(Integer.parseInt(body.get("id")), session.getUserId());
            send(ex, 200, JsonUtil.obj("status", JsonUtil.quote("ok")));
        } catch (Exception e) {
            send(ex, 400, JsonUtil.error(e.getMessage()));
        }
    }

    // Mark all as read: POST /api/notifications/read-all
    private void handleNotificationReadAll(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            send(ex, 405, JsonUtil.error("Use POST"));
            return;
        }
        AuthSession session = requireSession(ex);
        if (session == null) {
            return;
        }
        notificationService.markAllRead(session.getUserId());
        send(ex, 200, JsonUtil.obj("status", JsonUtil.quote("ok")));
    }

    // Fire-and-forget helper: in-app row + email, never breaks the request.
    private void notify(int userId, String type, String title, String message) {
        try {
            User u = userRepo.findById(userId);
            String email = u == null ? null : u.getEmail();
            notificationService.notify(userId, email, type, title, message);
        } catch (Exception ignored) {
        }
    }

    // ---- Proof of delivery (Phase 6) ----

    // Upload: POST /api/proofs {code|id, photo (base64 or data-URI), notes?, recipientName?}
    // Only the assigned rider (or an admin) may upload, and only while the
    // parcel is out for delivery or already delivered.
    private void handleProof(HttpExchange ex) throws IOException {
        if ("GET".equals(ex.getRequestMethod())) {
            handleProofGet(ex);
            return;
        }
        if (!"POST".equals(ex.getRequestMethod())) {
            send(ex, 405, JsonUtil.error("Use POST"));
            return;
        }
        AuthSession session = requireRole(ex, "COURIER", "ADMIN");
        if (session == null) {
            return;
        }
        try {
            Map<String, String> body = parseBody(ex.getRequestBody());
            Parcel p = resolveParcelByBody(body);
            if (p == null) {
                send(ex, 404, JsonUtil.error("Parcel not found"));
                return;
            }
            if (!"ADMIN".equals(session.getRole())
                    && !assignmentService.isAssignedTo(p.getParcelId(), session.getUserId())) {
                send(ex, 403, JsonUtil.error("Forbidden: proof belongs to your assigned deliveries."));
                return;
            }
            String cur = p.getCurrentStatus();
            if (!"OUT_FOR_DELIVERY".equals(cur) && !"DELIVERED".equals(cur)) {
                send(ex, 400, JsonUtil.error("A photo can only be uploaded when the parcel is OUT_FOR_DELIVERY or DELIVERED."));
                return;
            }

            // Decode the photo (accepts "data:image/png;base64,XXXX" or raw base64).
            String raw = body.get("photo");
            if (raw == null || raw.isBlank()) {
                send(ex, 400, JsonUtil.error("Photo is required."));
                return;
            }
            String mime = "image/jpeg";
            String ext = "jpg";
            String b64 = raw;
            if (raw.startsWith("data:")) {
                int comma = raw.indexOf(',');
                String head = raw.substring(0, comma < 0 ? 0 : comma);
                if (head.contains("png")) {
                    mime = "image/png";
                    ext = "png";
                } else if (head.contains("webp")) {
                    mime = "image/webp";
                    ext = "webp";
                }
                b64 = raw.substring(comma + 1);
            }
            byte[] bytes;
            try {
                bytes = Base64.getDecoder().decode(b64.trim());
            } catch (IllegalArgumentException e) {
                send(ex, 400, JsonUtil.error("Photo is not valid base64."));
                return;
            }
            if (bytes.length > 3 * 1024 * 1024) {
                send(ex, 413, JsonUtil.error("Photo too large (max 3 MB)."));
                return;
            }

            // Persist the file, then the DB record.
            String photoName = "proof_" + p.getParcelId() + "." + ext;
            java.io.File dir = new java.io.File("proofs");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            java.nio.file.Files.write(new java.io.File(dir, photoName).toPath(), bytes);
            DeliveryProof proof = deliveryProofService.save(p.getParcelId(), session.getUserId(),
                photoName, body.get("notes"), body.get("recipientName"));
            send(ex, 200, proofJson(proof));
        } catch (Exception e) {
            send(ex, 400, JsonUtil.error(e.getMessage()));
        }
    }

    // Metadata for one parcel: GET /api/proofs?code=..
    private void handleProofGet(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) {
            send(ex, 405, JsonUtil.error("Use GET"));
            return;
        }
        AuthSession session = requireSession(ex);
        if (session == null) {
            return;
        }
        try {
            Map<String, String> q = parseQuery(ex.getRequestURI().getRawQuery());
            Parcel p = resolveParcelByQuery(q);
            if (p == null) {
                send(ex, 404, JsonUtil.error("Parcel not found"));
                return;
            }
            if (!canReadProof(session, p)) {
                send(ex, 403, JsonUtil.error("Forbidden: no access to this proof."));
                return;
            }
            DeliveryProof proof = deliveryProofService.byParcel(p.getParcelId());
            send(ex, 200, proof == null
                ? "{\"status\":\"ok\",\"proof\":null}"
                : "{\"status\":\"ok\",\"proof\":" + proofJson(proof) + "}");
        } catch (Exception e) {
            send(ex, 400, JsonUtil.error(e.getMessage()));
        }
    }

    // The photo itself: GET /api/proofs/photo?code=..
    private void handleProofPhoto(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) {
            send(ex, 405, JsonUtil.error("Use GET"));
            return;
        }
        AuthSession session = requireSession(ex);
        if (session == null) {
            return;
        }
        try {
            Map<String, String> q = parseQuery(ex.getRequestURI().getRawQuery());
            Parcel p = resolveParcelByQuery(q);
            if (p == null) {
                send(ex, 404, JsonUtil.error("Parcel not found"));
                return;
            }
            if (!canReadProof(session, p)) {
                send(ex, 403, JsonUtil.error("Forbidden: no access to this proof."));
                return;
            }
            DeliveryProof proof = deliveryProofService.byParcel(p.getParcelId());
            if (proof == null) {
                send(ex, 404, JsonUtil.error("No photo uploaded yet."));
                return;
            }
            java.io.File f = new java.io.File("proofs", proof.getPhotoName());
            if (!f.exists()) {
                send(ex, 404, JsonUtil.error("Photo file missing."));
                return;
            }
            byte[] bytes = java.nio.file.Files.readAllBytes(f.toPath());
            String mime = proof.getPhotoName().endsWith(".png") ? "image/png"
                : proof.getPhotoName().endsWith(".webp") ? "image/webp" : "image/jpeg";
            ex.getResponseHeaders().set("Content-Type", mime);
            ex.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(bytes);
            }
        } catch (Exception e) {
            send(ex, 400, JsonUtil.error(e.getMessage()));
        }
    }

    // Sender, admin and the assigned rider may all see a proof.
    private boolean canReadProof(AuthSession session, Parcel p) {
        if ("ADMIN".equals(session.getRole())) {
            return true;
        }
        if (p.getCreatedByUserId() > 0 && p.getCreatedByUserId() == session.getUserId()) {
            return true;
        }
        return assignmentService.isAssignedTo(p.getParcelId(), session.getUserId());
    }

    private String proofJson(DeliveryProof proof) {
        return JsonUtil.obj(
            "proofId", String.valueOf(proof.getProofId()),
            "parcelId", String.valueOf(proof.getParcelId()),
            "riderUserId", String.valueOf(proof.getRiderUserId()),
            "photoName", JsonUtil.quote(proof.getPhotoName()),
            "notes", JsonUtil.quote(proof.getNotes() == null ? "" : proof.getNotes()),
            "recipientName", JsonUtil.quote(proof.getRecipientName() == null ? "" : proof.getRecipientName()),
            "deliveredAt", JsonUtil.quote(proof.getDeliveredAt() == null ? "" : proof.getDeliveredAt())
        );
    }

    // ---- Ratings & reviews (Phase 4) ----

    // Sender rates the rider after a completed delivery:
    // POST /api/ratings {code|id, rating (1-5), comment?}
    private void handleRating(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            send(ex, 405, JsonUtil.error("Use POST"));
            return;
        }
        if (requireSession(ex) == null) {
            return;
        }
        try {
            Map<String, String> body = parseBody(ex.getRequestBody());
            Parcel p = resolveParcelByBody(body);
            if (p == null) {
                send(ex, 404, JsonUtil.error("Parcel not found"));
                return;
            }
            AuthSession session = requireOwner(ex, p);
            if (session == null) {
                return;
            }
            if (!"DELIVERED".equals(p.getCurrentStatus())) {
                send(ex, 400, JsonUtil.error("Only delivered parcels can be rated."));
                return;
            }
            ParcelAssignment a = assignmentService.byParcel(p.getParcelId());
            int riderId = a == null ? 0 : a.getRiderUserId();
            Rating r = ratingService.rate(p.getParcelId(), session.getUserId(), riderId,
                Integer.parseInt(body.get("rating")), body.get("comment"));
            send(ex, 200, ratingJson(r));
        } catch (Exception e) {
            send(ex, 400, JsonUtil.error(e.getMessage()));
        }
    }

    // Did this parcel get a rating? GET /api/ratings/parcel?code=..
    private void handleRatingByParcel(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) {
            send(ex, 405, JsonUtil.error("Use GET"));
            return;
        }
        try {
            Map<String, String> q = parseQuery(ex.getRequestURI().getRawQuery());
            Parcel p = resolveParcelByQuery(q);
            if (p == null) {
                send(ex, 404, JsonUtil.error("Parcel not found"));
                return;
            }
            Rating r = ratingService.byParcel(p.getParcelId());
            send(ex, 200, r == null
                ? "{\"status\":\"ok\",\"rating\":null}"
                : "{\"status\":\"ok\",\"rating\":" + ratingJson(r) + "}");
        } catch (Exception e) {
            send(ex, 400, JsonUtil.error(e.getMessage()));
        }
    }

    // Rider reputation: GET /api/ratings/rider?riderId=..
    private void handleRatingByRider(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) {
            send(ex, 405, JsonUtil.error("Use GET"));
            return;
        }
        try {
            Map<String, String> q = parseQuery(ex.getRequestURI().getRawQuery());
            int riderId = Integer.parseInt(q.get("riderId"));
            ArrayList<Rating> list = ratingService.forRider(riderId);
            StringBuilder arr = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    arr.append(",");
                }
                arr.append(ratingJson(list.get(i)));
            }
            arr.append("]");
            send(ex, 200, JsonUtil.obj(
                "status", JsonUtil.quote("ok"),
                "riderUserId", String.valueOf(riderId),
                "average", String.valueOf(ratingService.averageForRider(riderId)),
                "count", String.valueOf(list.size()),
                "ratings", arr.toString()
            ));
        } catch (Exception e) {
            send(ex, 400, JsonUtil.error(e.getMessage()));
        }
    }

    private String ratingJson(Rating r) {
        return JsonUtil.obj(
            "ratingId", String.valueOf(r.getRatingId()),
            "parcelId", String.valueOf(r.getParcelId()),
            "riderUserId", String.valueOf(r.getRiderUserId()),
            "rating", String.valueOf(r.getStars()),
            "comment", JsonUtil.quote(r.getComment() == null ? "" : r.getComment()),
            "createdAt", JsonUtil.quote(r.getCreatedAt() == null ? "" : r.getCreatedAt())
        );
    }

    private String assignmentJson(ParcelAssignment a) {
        return JsonUtil.obj(
            "assignmentId", String.valueOf(a.getAssignmentId()),
            "parcelId", String.valueOf(a.getParcelId()),
            "riderUserId", String.valueOf(a.getRiderUserId()),
            "status", JsonUtil.quote(a.getStatus()),
            "reason", JsonUtil.quote(a.getReason() == null ? "" : a.getReason()),
            "assignedAt", JsonUtil.quote(a.getAssignedAt() == null ? "" : a.getAssignedAt()),
            "decidedAt", JsonUtil.quote(a.getDecidedAt() == null ? "" : a.getDecidedAt()),
            "completedAt", JsonUtil.quote(a.getCompletedAt() == null ? "" : a.getCompletedAt())
        );
    }

    // Work-queue rows: assignment + the parcel's booking details + route.
    private String jobsJson(ArrayList<ParcelAssignment> jobs) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < jobs.size(); i++) {
            ParcelAssignment a = jobs.get(i);
            if (i > 0) {
                sb.append(",");
            }
            Parcel p = parcelService.trackParcel(a.getParcelId());
            String parcelJson = p == null ? "{}" : JsonUtil.obj(
                "trackingCode", JsonUtil.quote(p.getTrackingCode()),
                "sender", JsonUtil.quote(p.getSenderName()),
                "receiver", JsonUtil.quote(p.getReceiverName()),
                "weightKg", String.valueOf(p.getWeightKg()),
                "fee", String.valueOf(p.getFee()),
                "vehicle", JsonUtil.quote(p.getVehicleType()),
                "pickupAddress", JsonUtil.quote(p.getPickUpAddress() == null ? "" : p.getPickUpAddress()),
                "dropoffAddress", JsonUtil.quote(p.getDropOffAddress() == null ? "" : p.getDropOffAddress()),
                "distanceKm", String.valueOf(p.getDistanceKm()),
                "interIsland", String.valueOf(p.isInterIsland()),
                "currentStatus", JsonUtil.quote(p.getCurrentStatus())
            );
            sb.append("{\"assignment\":" + assignmentJson(a) + ",\"parcel\":" + parcelJson + "}");
        }
        sb.append("]");
        return sb.toString();
    }

    private String randomPassword() {
        return "layag" + Integer.toHexString((int) (Math.random() * 0xFFFFFF));
    }

    private void handleAdminSummary(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) {
            send(ex, 405, JsonUtil.error("Use GET"));
            return;
        }
        if (requireRole(ex, "ADMIN") == null) {
            return;
        }
        DashboardSummary s = reportService.buildSummary();
        StringBuilder statusJson = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Long> e : s.getStatusCounts().entrySet()) {
            if (!first) {
                statusJson.append(",");
            }
            first = false;
            statusJson.append(JsonUtil.quote(e.getKey())).append(":").append(e.getValue());
        }
        statusJson.append("}");
        ArrayList<Parcel> recent = s.getRecentParcels();
        StringBuilder rec = new StringBuilder("[");
        for (int i = 0; i < recent.size(); i++) {
            if (i > 0) {
                rec.append(",");
            }
            Parcel p = recent.get(i);
            rec.append(JsonUtil.obj(
                "parcelId", String.valueOf(p.getParcelId()),
                "trackingCode", JsonUtil.quote(p.getTrackingCode()),
                "sender", JsonUtil.quote(p.getSenderName()),
                "receiver", JsonUtil.quote(p.getReceiverName()),
                "fee", String.valueOf(p.getFee()),
                "status", JsonUtil.quote(p.getCurrentStatus()),
                "vehicle", JsonUtil.quote(p.getVehicleType())
            ));
        }
        rec.append("]");
        send(ex, 200, JsonUtil.obj(
            "status", JsonUtil.quote("ok"),
            "totalParcels", String.valueOf(s.getTotalParcels()),
            "totalRevenue", String.valueOf(s.getTotalRevenue()),
            "averageFee", String.valueOf(s.getAverageFee()),
            "statusCounts", statusJson.toString(),
            "recentParcels", rec.toString()
        ));
    }

    private ArrayList<Parcel> filterByStatus(ArrayList<Parcel> list, String status) {
        ArrayList<Parcel> result = new ArrayList<>();
        for (Parcel p : list) {
            if (p.getCurrentStatus().equalsIgnoreCase(status)) {
                result.add(p);
            }
        }
        return result;
    }

    private String parcelsJson(ArrayList<Parcel> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            Parcel p = list.get(i);
            sb.append(JsonUtil.obj(
                "parcelId", String.valueOf(p.getParcelId()),
                "trackingCode", JsonUtil.quote(p.getTrackingCode()),
                "sender", JsonUtil.quote(p.getSenderName()),
                "receiver", JsonUtil.quote(p.getReceiverName()),
                "weightKg", String.valueOf(p.getWeightKg()),
                "fee", String.valueOf(p.getFee()),
                "currentStatus", JsonUtil.quote(p.getCurrentStatus()),
                "vehicle", JsonUtil.quote(p.getVehicleType()),
                "pickupAddress", JsonUtil.quote(p.getPickUpAddress()),
                "dropoffAddress", JsonUtil.quote(p.getDropOffAddress()),
                "distanceKm", String.valueOf(p.getDistanceKm()),
                "interIsland", String.valueOf(p.isInterIsland()),
                "confirmed", String.valueOf(p.isConfirmed())
            ));
        }
        sb.append("]");
        return sb.toString();
    }

    private Map<String, String> parseBody(InputStream in) throws IOException {
        String raw = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        return parseKeyValues(raw);
    }

    private Map<String, String> parseQuery(String raw) throws UnsupportedEncodingException {
        if (raw == null) {
            return new HashMap<>();
        }
        return parseKeyValues(raw);
    }

    private Map<String, String> parseKeyValues(String raw) throws UnsupportedEncodingException {
        Map<String, String> map = new HashMap<>();
        if (raw == null || raw.isEmpty()) {
            return map;
        }
        for (String pair : raw.split("&")) {
            String[] kv = pair.split("=", 2);
            String key = URLDecoder.decode(kv[0], StandardCharsets.UTF_8.name());
            String value = kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8.name()) : "";
            map.put(key, value);
        }
        return map;
    }

    private void send(HttpExchange ex, int code, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        String origin = ex.getRequestHeaders().getFirst("Origin");
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        // Only echo a browser's own origin back when it is in the whitelist.
        // Never send "*", and never reflect an unknown origin.
        if (origin != null && originAllowed(origin)) {
            ex.getResponseHeaders().set("Access-Control-Allow-Origin", origin);
            ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        }
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    // ---- CORS whitelist + preflight handling ----

    // Runs before every route. Handles CORS preflight (OPTIONS) and rejects
    // requests whose Origin is not in the whitelist. Returns true when the
    // request was already answered and the caller must stop.
    private boolean corsGuard(HttpExchange ex) throws IOException {
        String origin = ex.getRequestHeaders().getFirst("Origin");
        boolean preflight = "OPTIONS".equals(ex.getRequestMethod());

        if (origin != null && !originAllowed(origin)) {
            send(ex, 403, JsonUtil.error("Origin not allowed by CORS policy."));
            return true;
        }
        if (preflight) {
            if (origin == null) {
                send(ex, 400, JsonUtil.error("Preflight request without Origin header."));
                return true;
            }
            ex.getResponseHeaders().set("Access-Control-Allow-Origin", origin);
            ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
            ex.getResponseHeaders().set("Access-Control-Max-Age", "600");
            ex.sendResponseHeaders(204, -1);
            ex.close();
            return true;
        }
        return false;
    }

    private boolean originAllowed(String origin) {
        if (origin == null) {
            return true; // same-origin browser or curl-style client
        }
        String o = origin.trim();
        if ("null".equalsIgnoreCase(o)) {
            return ALLOW_FILE_ORIGIN; // file:// pages, dev only
        }
        for (String allowed : CORS_WHITELIST) {
            if (allowed.trim().equalsIgnoreCase(o)) {
                return true;
            }
        }
        return false;
    }

    // ---- Login/register brute-force protection ----

    // Client IP for rate limiting. We deliberately use the socket address and
    // NOT X-Forwarded-For: that header is client-controlled and trivially
    // spoofed, which would let an attacker rotate it to escape the lockout.
    private String clientIp(HttpExchange ex) {
        return ex.getRemoteAddress().getAddress().getHostAddress();
    }

    // Lockout time still in effect for a brand-new request (before counting it).
    private long lockRemaining(String ip) {
        AttemptRecord r = ipAttempts.get(ip);
        if (r == null) {
            return 0;
        }
        long t = r.lockedUntil - System.currentTimeMillis();
        return t > 0 ? t : 0;
    }

    // Records a failed attempt. Returns the lockout duration remaining once the
    // IP crosses the max-failures threshold, otherwise 0.
    private long registerFailure(String ip, int maxFailures, long lockMs) {
        AttemptRecord r = ipAttempts.computeIfAbsent(ip, k -> new AttemptRecord());
        long now = System.currentTimeMillis();
        if (r.lockedUntil > now) {
            return r.lockedUntil - now;
        }
        r.failures++;
        if (r.failures >= maxFailures) {
            r.lockedUntil = now + lockMs;
            r.failures = 0;
            return lockMs;
        }
        return 0;
    }

    private void clearAttempts(String ip) {
        ipAttempts.remove(ip);
    }

    // ---- Role-based access control helpers ----

    private String bearerToken(HttpExchange ex) {
        List<String> auth = ex.getRequestHeaders().get("Authorization");
        if (auth == null || auth.isEmpty()) {
            return null;
        }
        String header = auth.get(0);
        return header.startsWith("Bearer ") ? header.substring(7).trim() : header.trim();
    }

    // Valid session required. Sends 401 and returns null otherwise.
    private AuthSession requireSession(HttpExchange ex) throws IOException {
        AuthSession session = sessionService.validate(bearerToken(ex));
        if (session == null) {
            send(ex, 401, JsonUtil.error("Authentication required. Send: Authorization: Bearer <token>"));
            return null;
        }
        return session;
    }

    // Valid session AND one of the allowed roles. Sends 401/403 and returns null otherwise.
    private AuthSession requireRole(HttpExchange ex, String... roles) throws IOException {
        AuthSession session = requireSession(ex);
        if (session == null) {
            return null;
        }
        for (String role : roles) {
            if (role.equals(session.getRole())) {
                return session;
            }
        }
        send(ex, 403, JsonUtil.error("Forbidden: requires role " + Arrays.toString(roles)
            + " but session is " + session.getRole()));
        return null;
    }

    // Valid session AND the account that booked this parcel (admins pass too).
    // Stops a registered user from reading/charging payments on parcels they
    // did not create by guessing tracking codes.
    private AuthSession requireOwner(HttpExchange ex, Parcel p) throws IOException {
        AuthSession session = requireSession(ex);
        if (session == null) {
            return null;
        }
        if ("ADMIN".equals(session.getRole())) {
            return session;
        }
        if (p.getCreatedByUserId() > 0 && p.getCreatedByUserId() == session.getUserId()) {
            return session;
        }
        send(ex, 403, JsonUtil.error("Forbidden: you are not the owner of this parcel."));
        return null;
    }
}