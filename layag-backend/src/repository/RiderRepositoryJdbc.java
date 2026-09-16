package repository;

import model.RiderProfile;
import db.DbConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

public class RiderRepositoryJdbc implements RiderRepository {
    private static final String COLS = "rider_user_id, vehicle_type, is_available, max_concurrent, created_at";

    @Override
    public void createProfile(int riderUserId, String vehicleType, int maxConcurrent) {
        String sql = "INSERT INTO rider_profiles (rider_user_id, vehicle_type, max_concurrent) VALUES (?, ?, ?)";
        try (Connection conn = DbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, riderUserId);
            ps.setString(2, vehicleType);
            ps.setInt(3, maxConcurrent);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create rider profile: " + e.getMessage(), e);
        }
    }

    @Override
    public RiderProfile findById(int riderUserId) {
        String sql = "SELECT " + COLS + " FROM rider_profiles WHERE rider_user_id = ?";
        try (Connection conn = DbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, riderUserId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? map(rs) : null;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load rider profile: " + e.getMessage(), e);
        }
    }

    @Override
    public ArrayList<RiderProfile> findAll() {
        ArrayList<RiderProfile> list = new ArrayList<>();
        String sql = "SELECT " + COLS + " FROM rider_profiles ORDER BY rider_user_id";
        try (Connection conn = DbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load rider profiles: " + e.getMessage(), e);
        }
        return list;
    }

    @Override
    public void setAvailable(int riderUserId, boolean available) {
        String sql = "UPDATE rider_profiles SET is_available = ? WHERE rider_user_id = ?";
        try (Connection conn = DbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, available ? 1 : 0);
            ps.setInt(2, riderUserId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update rider availability: " + e.getMessage(), e);
        }
    }

    private RiderProfile map(ResultSet rs) throws SQLException {
        return new RiderProfile(
            rs.getInt("rider_user_id"),
            rs.getString("vehicle_type"),
            rs.getInt("is_available") == 1,
            rs.getInt("max_concurrent")
        );
    }
}