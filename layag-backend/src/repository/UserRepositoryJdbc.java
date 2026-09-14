package repository;

import model.User;
import db.DbConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;

public class UserRepositoryJdbc implements UserRepository {

    private static final String COLS = "user_id, name, email, password_hash, role, is_verified";

    @Override
    public User findByEmail(String email) {
        String sql = "SELECT " + COLS + " FROM users WHERE email = ?";
        try (Connection conn = DbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return map(rs);
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find user: " + e.getMessage(), e);
        }
    }

    @Override
    public User findById(int userId) {
        String sql = "SELECT " + COLS + " FROM users WHERE user_id = ?";
        try (Connection conn = DbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return map(rs);
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find user by id: " + e.getMessage(), e);
        }
    }

    @Override
    public int register(User user) {
        String sql = "INSERT INTO users (name, email, password_hash, role) VALUES (?, ?, ?, ?)";
        try (Connection conn = DbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, user.getName());
            ps.setString(2, user.getEmail());
            ps.setString(3, user.getPasswordHash());
            ps.setString(4, user.getRole());
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            return keys.next() ? keys.getInt(1) : 0;
        } catch (SQLIntegrityConstraintViolationException e) {
            // Duplicate email surfaces as a clean, client-safe message (no SQL internals).
            throw new IllegalArgumentException("Email already registered.");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to register user: " + e.getMessage(), e);
        }
    }

    @Override
    public void updateProfile(int userId, String name, String passwordHash) {
        String sql = "UPDATE users SET name = ?, password_hash = ? WHERE user_id = ?";
        try (Connection conn = DbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, passwordHash);
            ps.setInt(3, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update profile: " + e.getMessage(), e);
        }
    }

    @Override
    public void setVerified(int userId) {
        String sql = "UPDATE users SET is_verified = 1 WHERE user_id = ?";
        try (Connection conn = DbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to verify user: " + e.getMessage(), e);
        }
    }

    private User map(ResultSet rs) throws SQLException {
        return new User(
            rs.getInt("user_id"),
            rs.getString("name"),
            rs.getString("email"),
            rs.getString("password_hash"),
            rs.getString("role"),
            rs.getInt("is_verified") == 1
        );
    }
}