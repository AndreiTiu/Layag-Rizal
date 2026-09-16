package repository;

import model.Notification;
import db.DbConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;

public class NotificationRepositoryJdbc implements NotificationRepository {
    private static final String COLS = "notification_id, user_id, type, title, message, is_read, created_at";

    @Override
    public Notification create(int userId, String type, String title, String message) {
        String sql = "INSERT INTO notifications (user_id, type, title, message) VALUES (?, ?, ?, ?)";
        try (Connection conn = DbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, userId);
            ps.setString(2, type);
            ps.setString(3, title);
            ps.setString(4, message);
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            int id = keys.next() ? keys.getInt(1) : 0;
            return new Notification(id, userId, type, title, message, false, now());
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create notification: " + e.getMessage(), e);
        }
    }

    @Override
    public ArrayList<Notification> findByUser(int userId, int limit) {
        ArrayList<Notification> list = new ArrayList<>();
        String sql = "SELECT " + COLS + " FROM notifications WHERE user_id = ? ORDER BY notification_id DESC LIMIT " + limit;
        try (Connection conn = DbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load notifications: " + e.getMessage(), e);
        }
        return list;
    }

    @Override
    public int unreadCount(int userId) {
        String sql = "SELECT COUNT(*) FROM notifications WHERE user_id = ? AND is_read = 0";
        try (Connection conn = DbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to count notifications: " + e.getMessage(), e);
        }
    }

    @Override
    public void markRead(int notificationId, int userId) {
        String sql = "UPDATE notifications SET is_read = 1 WHERE notification_id = ? AND user_id = ?";
        try (Connection conn = DbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, notificationId);
            ps.setInt(2, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to mark notification read: " + e.getMessage(), e);
        }
    }

    @Override
    public void markAllRead(int userId) {
        String sql = "UPDATE notifications SET is_read = 1 WHERE user_id = ?";
        try (Connection conn = DbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to mark notifications read: " + e.getMessage(), e);
        }
    }

    private Notification map(ResultSet rs) throws SQLException {
        return new Notification(
            rs.getInt("notification_id"),
            rs.getInt("user_id"),
            rs.getString("type"),
            rs.getString("title"),
            rs.getString("message"),
            rs.getInt("is_read") == 1,
            rs.getTimestamp("created_at") == null ? "" : rs.getString("created_at")
        );
    }

    private String now() {
        return new java.sql.Timestamp(System.currentTimeMillis()).toString();
    }
}