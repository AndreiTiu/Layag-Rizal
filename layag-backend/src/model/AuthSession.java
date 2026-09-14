package model;

// One logged-in session. Created on login, checked on every protected request.
// Mimics a JWT/session token so the API is role-protected without a framework.
public class AuthSession {
    private String token;
    private int userId;
    private String role;
    private String name;
    private long createdAtMillis;
    private long expiresAtMillis;

    public AuthSession(String token, int userId, String role, String name,
                        long createdAtMillis, long expiresAtMillis) {
        this.token = token;
        this.userId = userId;
        this.role = role;
        this.name = name;
        this.createdAtMillis = createdAtMillis;
        this.expiresAtMillis = expiresAtMillis;
    }

    public String getToken() { return token; }
    public int getUserId() { return userId; }
    public String getRole() { return role; }
    public String getName() { return name; }
    public long getCreatedAtMillis() { return createdAtMillis; }
    public long getExpiresAtMillis() { return expiresAtMillis; }
}