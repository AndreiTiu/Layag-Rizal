package repository;

import model.Rating;
import db.DbConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;

public class RatingRepositoryJdbc implements RatingRepository {
    private static final String COLS = "rating_id, parcel_id, user_id, rider_user_id, rating, comment, created_at";

    @Override
    public Rating create(int parcelId, int userId, int riderUserId, int stars, String comment) {
        String sql = "INSERT INTO ratings (parcel_id, user_id, rider_user_id, rating, comment) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, parcelId);
            ps.setInt(2, userId);
            ps.setInt(3, riderUserId);
            ps.setInt(4, stars);
            ps.setString(5, comment);
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            int id = keys.next() ? keys.getInt(1) : 0;
            return new Rating(id, parcelId, userId, riderUserId, stars, comment, now());
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create rating: " + e.getMessage(), e);
        }
    }

    @Override
    public Rating findByParcel(int parcelId) {
        String sql = "SELECT " + COLS + " FROM ratings WHERE parcel_id = ?";
        try (Connection conn = DbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, parcelId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? map(rs) : null;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load rating: " + e.getMessage(), e);
        }
    }

    @Override
    public ArrayList<Rating> findByRider(int riderUserId) {
        ArrayList<Rating> list = new ArrayList<>();
        String sql = "SELECT " + COLS + " FROM ratings WHERE rider_user_id = ? ORDER BY created_at DESC";
        try (Connection conn = DbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, riderUserId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load ratings: " + e.getMessage(), e);
        }
        return list;
    }

    @Override
    public double averageForRider(int riderUserId) {
        String sql = "SELECT COALESCE(AVG(rating),0) FROM ratings WHERE rider_user_id = ?";
        try (Connection conn = DbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, riderUserId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? Math.round(rs.getDouble(1) * 10.0) / 10.0 : 0;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to compute rating average: " + e.getMessage(), e);
        }
    }

    private Rating map(ResultSet rs) throws SQLException {
        return new Rating(
            rs.getInt("rating_id"),
            rs.getInt("parcel_id"),
            rs.getInt("user_id"),
            rs.getInt("rider_user_id"),
            rs.getInt("rating"),
            rs.getString("comment"),
            rs.getTimestamp("created_at") == null ? "" : rs.getString("created_at")
        );
    }

    private String now() {
        return new java.sql.Timestamp(System.currentTimeMillis()).toString();
    }
}