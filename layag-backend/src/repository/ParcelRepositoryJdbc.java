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
    @Override
    public Parcel registerParcel(String sender, String receiver, double weight, double fee, String vehicleType, String trackingCode, String serviceType, int createdByUserId) {
        String sql = "INSERT INTO parcels (sender_name, receiver_name, weight_kg, current_status, fee, vehicle_type, tracking_code, service_type, created_by_user_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
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
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            int id = keys.next() ? keys.getInt(1) : 0;
            Parcel p = new Parcel(id, sender, receiver, weight, fee, vehicleType, trackingCode);
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
        String sql = "SELECT parcel_id, sender_name, receiver_name, weight_kg, current_status, fee, vehicle_type, tracking_code, service_type, created_by_user_id FROM parcels";
        try (Connection conn = DbConnection.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                Parcel p = new Parcel(
                    rs.getInt("parcel_id"),
                    rs.getString("sender_name"),
                    rs.getString("receiver_name"),
                    rs.getDouble("weight_kg"),
                    rs.getDouble("fee"),
                    rs.getString("vehicle_type")
                );
                p.setCurrentStatus(rs.getString("current_status"));
                p.setTrackingCode(rs.getString("tracking_code"));
                p.setServiceType(rs.getString("service_type"));
                p.setCreatedByUserId(rs.getInt("created_by_user_id"));
                parcels.add(p);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load parcels: " + e.getMessage(), e);
        }
        return parcels;
    }

    @Override
    public Parcel findById(int id) {
        String sql = "SELECT parcel_id, sender_name, receiver_name, weight_kg, current_status, fee, vehicle_type, tracking_code, service_type, created_by_user_id FROM parcels WHERE parcel_id = ?";
        try (Connection conn = DbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                Parcel p = new Parcel(
                    rs.getInt("parcel_id"),
                    rs.getString("sender_name"),
                    rs.getString("receiver_name"),
                    rs.getDouble("weight_kg"),
                    rs.getDouble("fee"),
                    rs.getString("vehicle_type")
                );
                p.setCurrentStatus(rs.getString("current_status"));
                p.setTrackingCode(rs.getString("tracking_code"));
                p.setServiceType(rs.getString("service_type"));
                p.setCreatedByUserId(rs.getInt("created_by_user_id"));
                return p;
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find parcel: " + e.getMessage(), e);
        }
    }

    @Override
    public Parcel findByTrackingCode(String trackingCode) {
        String sql = "SELECT parcel_id, sender_name, receiver_name, weight_kg, current_status, fee, vehicle_type, tracking_code, service_type, created_by_user_id FROM parcels WHERE tracking_code = ?";
        try (Connection conn = DbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, trackingCode);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                Parcel p = new Parcel(
                    rs.getInt("parcel_id"),
                    rs.getString("sender_name"),
                    rs.getString("receiver_name"),
                    rs.getDouble("weight_kg"),
                    rs.getDouble("fee"),
                    rs.getString("vehicle_type")
                );
                p.setCurrentStatus(rs.getString("current_status"));
                p.setTrackingCode(rs.getString("tracking_code"));
                p.setServiceType(rs.getString("service_type"));
                p.setCreatedByUserId(rs.getInt("created_by_user_id"));
                return p;
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find parcel by tracking code: " + e.getMessage(), e);
        }
    }

    @Override
    public ArrayList<Parcel> findByStatus(String status) {
        ArrayList<Parcel> parcels = new ArrayList<>();
        String sql = "SELECT parcel_id, sender_name, receiver_name, weight_kg, current_status, fee, vehicle_type, tracking_code, service_type, created_by_user_id FROM parcels WHERE current_status = ? ORDER BY parcel_id";
        try (Connection conn = DbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Parcel p = new Parcel(
                    rs.getInt("parcel_id"),
                    rs.getString("sender_name"),
                    rs.getString("receiver_name"),
                    rs.getDouble("weight_kg"),
                    rs.getDouble("fee"),
                    rs.getString("vehicle_type")
                );
                p.setCurrentStatus(rs.getString("current_status"));
                p.setTrackingCode(rs.getString("tracking_code"));
                p.setServiceType(rs.getString("service_type"));
                p.setCreatedByUserId(rs.getInt("created_by_user_id"));
                parcels.add(p);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find parcels by status: " + e.getMessage(), e);
        }
        return parcels;
    }

    @Override
    public ArrayList<Parcel> search(String term) {
        ArrayList<Parcel> parcels = new ArrayList<>();
        String like = "%" + (term == null ? "" : term) + "%";
        String sql = "SELECT parcel_id, sender_name, receiver_name, weight_kg, current_status, fee, vehicle_type, tracking_code, service_type, created_by_user_id FROM parcels WHERE sender_name LIKE ? OR receiver_name LIKE ? OR tracking_code LIKE ? ORDER BY parcel_id";
        try (Connection conn = DbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, like);
            ps.setString(2, like);
            ps.setString(3, like);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Parcel p = new Parcel(
                    rs.getInt("parcel_id"),
                    rs.getString("sender_name"),
                    rs.getString("receiver_name"),
                    rs.getDouble("weight_kg"),
                    rs.getDouble("fee"),
                    rs.getString("vehicle_type")
                );
                p.setCurrentStatus(rs.getString("current_status"));
                p.setTrackingCode(rs.getString("tracking_code"));
                p.setServiceType(rs.getString("service_type"));
                p.setCreatedByUserId(rs.getInt("created_by_user_id"));
                parcels.add(p);
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
}