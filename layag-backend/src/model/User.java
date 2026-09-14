package model;

public class User {
    private int userId;
    private String name;
    private String email;
    private String passwordHash;
    private String role;
    private boolean verified;

    public User(int userId, String name, String email, String passwordHash, String role) {
        this(userId, name, email, passwordHash, role, false);
    }

    public User(int userId, String name, String email, String passwordHash, String role, boolean verified) {
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.verified = verified;
    }

    public int getUserId() { return userId; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getRole() { return role; }
    public boolean isVerified() { return verified; }
}