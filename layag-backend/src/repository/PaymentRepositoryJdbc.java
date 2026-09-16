package repository;

import model.Payment;
import db.DbConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;

public class PaymentRepositoryJdbc implements PaymentRepository {
    private static final String COLS = "payment_id, parcel_id, amount, method, status, reference, paid_at, created_at";

    @Override
    public Payment create(int parcelId, double amount, String method) {
        String sql = "INSERT INTO payments (parcel_id, amount, method) VALUES (?, ?, ?)";
        try (Connection conn = DbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, parcelId);
            ps.setDouble(2, amount);
            ps.setString(3, method);
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            int id = keys.next() ? keys.getInt(1) : 0;
            return new Payment(id, parcelId, amount, method, "PENDING", null, null, null);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create payment: " + e.getMessage(), e);
        }
    }

    @Override
    public Payment findByParcelId(int parcelId) {
        String sql = "SELECT " + COLS + " FROM payments WHERE parcel_id = ? ORDER BY payment_id DESC LIMIT 1";
        try (Connection conn = DbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, parcelId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? map(rs) : null;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find payment: " + e.getMessage(), e);
        }
    }

    @Override
    public ArrayList<Payment> findAll() {
        ArrayList<Payment> list = new ArrayList<>();
        String sql = "SELECT " + COLS + " FROM payments ORDER BY payment_id";
        try (Connection conn = DbConnection.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                list.add(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load payments: " + e.getMessage(), e);
        }
        return list;
    }

    @Override
    public void updateStatus(int paymentId, String status, String reference) {
        // paid_at is written by MySQL when the status becomes COMPLETED.
        String sql = "UPDATE payments SET status = ?, reference = ?, paid_at = IF(? = 'COMPLETED', NOW(), paid_at) WHERE payment_id = ?";
        try (Connection conn = DbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, reference);
            ps.setString(3, status);
            ps.setInt(4, paymentId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update payment: " + e.getMessage(), e);
        }
    }

    @Override
    public void updateAmount(int paymentId, double amount) {
        String sql = "UPDATE payments SET amount = ? WHERE payment_id = ? AND status = 'PENDING'";
        try (Connection conn = DbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, amount);
            ps.setInt(2, paymentId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update payment amount: " + e.getMessage(), e);
        }
    }

    private Payment map(ResultSet rs) throws SQLException {
        return new Payment(
            rs.getInt("payment_id"),
            rs.getInt("parcel_id"),
            rs.getDouble("amount"),
            rs.getString("method"),
            rs.getString("status"),
            rs.getString("reference"),
            rs.getTimestamp("paid_at") == null ? null : rs.getString("paid_at"),
            rs.getString("created_at")
        );
    }
}