package service;

import model.AuthSession;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// In-memory session store. Login issues a random token, protected endpoints
// present it as "Authorization: Bearer <token>" and we look it up here.
// Sessions expire after SESSION_TTL_MS (default 24h) for basic security.
public class AuthSessionService {
    private static final long SESSION_TTL_MS = 24 * 60 * 60 * 1000L; // 24h

    private final Map<String, AuthSession> sessions = Collections.synchronizedMap(new HashMap<>());

    public AuthSession createSession(int userId, String role, String name) {
        String token = UUID.randomUUID().toString().replace("-", "");
        long now = System.currentTimeMillis();
        AuthSession session = new AuthSession(token, userId, role, name, now, now + SESSION_TTL_MS);
        sessions.put(token, session);
        return session;
    }

    // Returns the session if the token is valid and not expired, else null.
    public AuthSession validate(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        AuthSession session = sessions.get(token);
        if (session == null) {
            return null;
        }
        if (System.currentTimeMillis() > session.getExpiresAtMillis()) {
            sessions.remove(token);
            return null;
        }
        return session;
    }

    public void logout(String token) {
        sessions.remove(token);
    }
}