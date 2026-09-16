package repository;

import model.Parcel;
import db.DbConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;

public class ParcelRepositoryJdbc implements ParcelRepository {
    private static final String COLS =
        "parcel_id, sender_name, receiver_name, weight_kg, current_status, fee, vehicle_type, " +
        "tracking_code, service_type, created_by_user_id, pick_up_address, drop_off_address, " +
        "distance_km, inter_island, confirmed";

    @Override
    public Parcel registerParcel(String sender, String receiver, double weight, double fee, String vehicleType, String trackingCode, String serviceType, int createdByUserId, String pickUpAddress, String dropOffAddress, double distanceKm, boolean interIsland) {
        String sql = "INSERT INTO parcels (sender_name, receiver_name, weight_kg, current_status, fee, vehicle_type, tracking_code, service_type, created_by_user_id, pick_up_address, drop_off_address, distance_km, inter_island) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, sender);
            ps.setString(2, receiver);
            ps.setDouble(3, weight);
            ps.setString(4, "REGISTERED");
            ps.setDouble(5, fee);
            ps.setString(6, vehicleType);
            ps.setString(7, trackingCode);
            ps.setString(8, serviceType);
            ps.setInt(9, createdByUserId);
            ps.setString(10, pickUpAddress);
            ps.setString(11, dropOffAddress);
            ps.setDouble(12, distanceKm);
            ps.setBoolean(13, interIsland);
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            int id = keys.next() ? keys.getInt(1) : 0;
            Parcel p = new Parcel(id, sender, receiver, weight, fee, vehicleType, trackingCode, pickUpAddress, dropOffAddress, distanceKm, interIsland, false);
            p.setServiceType(serviceType);
            p.setCreatedByUserId(createdByUserId);
            return p;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to register parcel: " + e.getMessage(), e);
        }
    }

    @Override
    public ArrayList<Parcel> findAll() {
        ArrayList<Parcel> parcels = new ArrayList<>();
        String sql = "SELECT " + COLS + " FROM parcels";
        try (Connection conn = DbConnection.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                parcels.add(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load parcels: " + e.getMessage(), e);
        }
        return parcels;
    }

    @Override
    public Parcel findById(int id) {
        String sql = "SELECT " + COLS + " FROM parcels WHERE parcel_id = ?";
        try (Connection conn = DbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? map(rs) : null;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find parcel: " + e.getMessage(), e);
        }
    }

    @Override
    public Parcel findByTrackingCode(String trackingCode) {
        String sql = "SELECT " + COLS + " FROM parcels WHERE tracking_code = ?";
        try (Connection conn = DbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, trackingCode);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? map(rs) : null;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find parcel by tracking code: " + e.getMessage(), e);
        }
    }

    @Override
    public ArrayList<Parcel> findByStatus(String status) {
        ArrayList<Parcel> parcels = new ArrayList<>();
        String sql = "SELECT " + COLS + " FROM parcels WHERE current_status = ? ORDER BY parcel_id";
        try (Connection conn = DbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                parcels.add(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find parcels by status: " + e.getMessage(), e);
        }
        return parcels;
    }

    @Override
    public ArrayList<Parcel> findByCreatedBy(int userId) {
        ArrayList<Parcel> parcels = new ArrayList<>();
        String sql = "SELECT " + COLS + " FROM parcels WHERE created_by_user_id = ? ORDER BY parcel_id";
        try (Connection conn = DbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                parcels.add(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find parcels by owner: " + e.getMessage(), e);
        }
        return parcels;
    }

    @Override
    public ArrayList<Parcel> search(String term) {
        ArrayList<Parcel> parcels = new ArrayList<>();
        String like = "%" + (term == null ? "" : term) + "%";
        String sql = "SELECT " + COLS + " FROM parcels WHERE sender_name LIKE ? OR receiver_name LIKE ? OR tracking_code LIKE ? ORDER BY parcel_id";
        try (Connection conn = DbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, like);
            ps.setString(2, like);
            ps.setString(3, like);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                parcels.add(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to search parcels: " + e.getMessage(), e);
        }
        return parcels;
    }

    @Override
    public void updateStatus(int id, String status) {
        String sql = "UPDATE parcels SET current_status = ? WHERE parcel_id = ?";
        try (Connection conn = DbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update status: " + e.getMessage(), e);
        }
    }

    @Override
    public void confirm(int id) {
        String sql = "UPDATE parcels SET confirmed = 1 WHERE parcel_id = ?";
        try (Connection conn = DbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to confirm parcel: " + e.getMessage(), e);
        }
    }

    @Override
    public void updateDetails(int id, String sender, String receiver, String pickUpAddress, String dropOffAddress, double weight, double fee, String vehicleType, String serviceType, double distanceKm, boolean interIsland) {
        String sql = "UPDATE parcels SET sender_name = ?, receiver_name = ?, pick_up_address = ?, drop_off_address = ?, weight_kg = ?, fee = ?, vehicle_type = ?, service_type = ?, distance_km = ?, inter_island = ? WHERE parcel_id = ?";
        try (Connection conn = DbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sender);
            ps.setString(2, receiver);
            ps.setString(3, pickUpAddress);
            ps.setString(4, dropOffAddress);
            ps.setDouble(5, weight);
            ps.setDouble(6, fee);
            ps.setString(7, vehicleType);
            ps.setString(8, serviceType);
            ps.setDouble(9, distanceKm);
            ps.setBoolean(10, interIsland);
            ps.setInt(11, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update parcel details: " + e.getMessage(), e);
        }
    }

    @Override
    public int count() {
        String sql = "SELECT COUNT(*) FROM parcels";
        try (Connection conn = DbConnection.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to count parcels: " + e.getMessage(), e);
        }
    }

    @Override
    public long countByStatus(String status) {
        String sql = "SELECT COUNT(*) FROM parcels WHERE current_status = ?";
        try (Connection conn = DbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getLong(1) : 0;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to count by status: " + e.getMessage(), e);
        }
    }

    @Override
    public double totalFees() {
        String sql = "SELECT COALESCE(SUM(fee), 0) FROM parcels";
        try (Connection conn = DbConnection.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getDouble(1) : 0.0;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to sum fees: " + e.getMessage(), e);
        }
    }

    @Override
    public double averageFee() {
        String sql = "SELECT COALESCE(AVG(fee), 0) FROM parcels";
        try (Connection conn = DbConnection.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getDouble(1) : 0.0;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to average fees: " + e.getMessage(), e);
        }
    }

    private Parcel map(ResultSet rs) throws SQLException {
        Parcel p = new Parcel(
            rs.getInt("parcel_id"),
            rs.getString("sender_name"),
            rs.getString("receiver_name"),
            rs.getDouble("weight_kg"),
            rs.getDouble("fee"),
            rs.getString("vehicle_type"),
            rs.getString("tracking_code"),
            rs.getString("pick_up_address"),
            rs.getString("drop_off_address"),
            rs.getDouble("distance_km"),
            rs.getBoolean("inter_island"),
            rs.getBoolean("confirmed")
        );
        p.setCurrentStatus(rs.getString("current_status"));
        p.setServiceType(rs.getString("service_type"));
        p.setCreatedByUserId(rs.getInt("created_by_user_id"));
        return p;
    }
}