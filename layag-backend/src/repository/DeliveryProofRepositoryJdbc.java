package repository;

import model.DeliveryProof;
import db.DbConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class DeliveryProofRepositoryJdbc implements DeliveryProofRepository {
    private static final String COLS = "proof_id, parcel_id, rider_user_id, photo_name, notes, recipient_name, delivered_at";

    @Override
    public DeliveryProof upsert(int parcelId, int riderUserId, String photoName, String notes, String recipientName) {
        String sql = "INSERT INTO delivery_proofs (parcel_id, rider_user_id, photo_name, notes, recipient_name) " +
            "VALUES (?, ?, ?, ?, ?) " +
            "ON DUPLICATE KEY UPDATE rider_user_id = VALUES(rider_user_id), photo_name = VALUES(photo_name), " +
            "notes = VALUES(notes), recipient_name = VALUES(recipient_name)";
        try (Connection conn = DbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, parcelId);
            ps.setInt(2, riderUserId);
            ps.setString(3, photoName);
            ps.setString(4, notes);
            ps.setString(5, recipientName);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save delivery proof: " + e.getMessage(), e);
        }
        return findByParcel(parcelId);
    }

    @Override
    public DeliveryProof findByParcel(int parcelId) {
        String sql = "SELECT " + COLS + " FROM delivery_proofs WHERE parcel_id = ?";
        try (Connection conn = DbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, parcelId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? map(rs) : null;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load delivery proof: " + e.getMessage(), e);
        }
    }

    private DeliveryProof map(ResultSet rs) throws SQLException {
        return new DeliveryProof(
            rs.getInt("proof_id"),
            rs.getInt("parcel_id"),
            rs.getInt("rider_user_id"),
            rs.getString("photo_name"),
            rs.getString("notes"),
            rs.getString("recipient_name"),
            rs.getTimestamp("delivered_at") == null ? "" : rs.getString("delivered_at")
        );
    }
}