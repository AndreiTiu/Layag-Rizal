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

public class ApiServer {
    private final ParcelService parcelService;
    private final TrackingService trackingService;
    private final AuthService authService;
    private final GpsService gpsService;
    private final ReportService reportService;
    private final AuthSessionService sessionService;
    private final PaymentService paymentService;
    private final FeeService feeService;
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
        server.createContext("/api/parcels/status", ex -> { if (api.corsGuard(ex)) return; api.handleStatus(ex); });
        server.createContext("/api/gps", ex -> { if (api.corsGuard(ex)) return; api.handleGps(ex); });
        server.createContext("/api/admin/summary", ex -> { if (api.corsGuard(ex)) return; api.handleAdminSummary(ex); });
        server.createContext("/api/users/profile", ex -> { if (api.corsGuard(ex)) return; api.handleProfile(ex); });
        server.createContext("/api/payments/charge", ex -> { if (api.corsGuard(ex)) return; api.handlePaymentCharge(ex); });
        server.createContext("/api/payments/confirm-collect", ex -> { if (api.corsGuard(ex)) return; api.handlePaymentCollect(ex); });
        server.createContext("/api/payments/parcel", ex -> { if (api.corsGuard(ex)) return; api.handlePaymentByParcel(ex); });
        server.createContext("/api/payments/receipt", ex -> { if (api.corsGuard(ex)) return; api.handlePaymentReceipt(ex); });
        server.createContext("/api/admin/payments", ex -> { if (api.corsGuard(ex)) return; api.handleAdminPayments(ex); });
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
                Parcel p = parcelService.registerParcel(
                    body.get("sender"),
                    body.get("receiver"),
                    weight,
                    body.get("service") != null ? body.get("service") : "regular",
                    body.get("vehicle") != null ? body.get("vehicle") : "VAN",
                    false,
                    session.getUserId()
                );
                trackingService.recordInitialStatus(p.getParcelId(), body.getOrDefault("updatedBy", "branch_staff"));
                // One transaction per parcel: COD (default) or ONLINE (mock gateway).
                String method = body.getOrDefault("paymentMethod", "COD").toUpperCase();
                paymentService.createForParcel(p.getParcelId(), p.getFee(), method);
                send(ex, 200, JsonUtil.obj(
                    "status", JsonUtil.quote("ok"),
                    "parcelId", String.valueOf(p.getParcelId()),
                    "trackingCode", JsonUtil.quote(p.getTrackingCode()),
                    "fee", String.valueOf(p.getFee()),
                    "currentStatus", JsonUtil.quote(p.getCurrentStatus()),
                    "vehicle", JsonUtil.quote(p.getVehicleType()),
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
                    "vehicle", JsonUtil.quote(p.getVehicleType())
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
        if (requireRole(ex, "COURIER", "ADMIN") == null) {
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

            trackingService.updateStatus(p.getParcelId(), toStatus, location, updatedBy, p.getCurrentStatus());
            parcelService.updateStatus(p.getParcelId(), toStatus);
            // Cash on delivery: payment completes the moment cash is collected.
            if ("DELIVERED".equals(toStatus)) {
                Payment pmt = paymentService.getByParcel(p.getParcelId());
                if (pmt != null && "COD".equals(pmt.getMethod())) {
                    paymentService.confirmCollected(p.getParcelId());
                }
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

    // ---- Payments ----

    // ONLINE: customer pays through the (mock) gateway -> POST /api/payments/charge
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
            // Itemized parts (rebuilt from stored inputs). interIsland is not stored
            // on the parcel, so the surcharge is derived: whatever is above the base.
            FeeBreakdown bd = feeService.computeBreakdown(p.getWeightKg(), p.getServiceType(), p.getVehicleType(), false);
            double surcharge = Math.max(0, p.getFee() - bd.getTotalFee());
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
        int codCount = 0, onlineCount = 0;
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
            if ("COD".equals(pmt.getMethod())) codCount++; else onlineCount++;
        }
        arr.append("]");
        send(ex, 200, JsonUtil.obj(
            "status", JsonUtil.quote("ok"),
            "totalCollected", String.valueOf(collected),
            "totalPending", String.valueOf(pending),
            "codCount", String.valueOf(codCount),
            "onlineCount", String.valueOf(onlineCount),
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
                "vehicle", JsonUtil.quote(p.getVehicleType())
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