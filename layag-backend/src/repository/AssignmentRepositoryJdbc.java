package repository;

import model.ParcelAssignment;
import db.DbConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;

public class AssignmentRepositoryJdbc implements AssignmentRepository {
    private static final String COLS =
        "assignment_id, parcel_id, rider_user_id, status, reason, assigned_at, decided_at, completed_at";

    @Override
    public ParcelAssignment create(int parcelId, int riderUserId, String reason) {
        String sql = "INSERT INTO parcel_assignments (parcel_id, rider_user_id, status, reason) VALUES (?, ?, 'ASSIGNED', ?)";
        try (Connection conn = DbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, parcelId);
            ps.setInt(2, riderUserId);
            ps.setString(3, reason);
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            int id = keys.next() ? keys.getInt(1) : 0;
            return new ParcelAssignment(id, parcelId, riderUserId, "ASSIGNED", reason, now(), null, null);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create assignment: " + e.getMessage(), e);
        }
    }

    @Override
    public ParcelAssignment findById(int assignmentId) {
        String sql = "SELECT " + COLS + " FROM parcel_assignments WHERE assignment_id = ?";
        try (Connection conn = DbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, assignmentId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? map(rs) : null;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load assignment: " + e.getMessage(), e);
        }
    }

    @Override
    public ParcelAssignment findByParcel(int parcelId) {
        String sql = "SELECT " + COLS + " FROM parcel_assignments WHERE parcel_id = ?";
        try (Connection conn = DbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, parcelId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? map(rs) : null;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load assignment: " + e.getMessage(), e);
        }
    }

    @Override
    public ArrayList<ParcelAssignment> findByRider(int riderUserId, String... statuses) {
        ArrayList<ParcelAssignment> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT " + COLS + " FROM parcel_assignments WHERE rider_user_id = ?");
        if (statuses != null && statuses.length > 0) {
            sql.append(" AND status IN (");
            for (int i = 0; i < statuses.length; i++) {
                if (i > 0) {
                    sql.append(",");
                }
                sql.append("?");
            }
            sql.append(")");
        }
        sql.append(" ORDER BY assignment_id DESC");
        try (Connection conn = DbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            ps.setInt(1, riderUserId);
            for (int i = 0; i < statuses.length; i++) {
                ps.setString(i + 2, statuses[i]);
            }
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load assignments: " + e.getMessage(), e);
        }
        return list;
    }

    @Override
    public void updateStatus(int assignmentId, String status, String reason) {
        String sql = "UPDATE parcel_assignments SET status = ?, reason = ?, " +
            "decided_at = IF(status IN ('ASSIGNED'), NOW(), decided_at), " +
            "completed_at = IF(? IN ('COMPLETED','FAILED'), NOW(), completed_at) WHERE assignment_id = ?";
        try (Connection conn = DbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, reason);
            ps.setString(3, status);
            ps.setInt(4, assignmentId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update assignment: " + e.getMessage(), e);
        }
    }

    @Override
    public void moveTo(int assignmentId, int newRiderUserId, String reason) {
        String sql = "UPDATE parcel_assignments SET rider_user_id = ?, status = 'ASSIGNED', reason = ?, " +
            "decided_at = NULL, completed_at = NULL WHERE assignment_id = ?";
        try (Connection conn = DbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, newRiderUserId);
            ps.setString(2, reason);
            ps.setInt(3, assignmentId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to move assignment to another rider: " + e.getMessage(), e);
        }
    }

    @Override
    public int countOpen(int riderUserId) {
        String sql = "SELECT COUNT(*) FROM parcel_assignments WHERE rider_user_id = ? AND status IN ('ASSIGNED','ACCEPTED')";
        try (Connection conn = DbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, riderUserId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to count assignments: " + e.getMessage(), e);
        }
    }

    private ParcelAssignment map(ResultSet rs) throws SQLException {
        return new ParcelAssignment(
            rs.getInt("assignment_id"),
            rs.getInt("parcel_id"),
            rs.getInt("rider_user_id"),
            rs.getString("status"),
            rs.getString("reason"),
            rs.getTimestamp("assigned_at") == null ? null : rs.getString("assigned_at"),
            rs.getTimestamp("decided_at") == null ? null : rs.getString("decided_at"),
            rs.getTimestamp("completed_at") == null ? null : rs.getString("completed_at")
        );
    }

    private String now() {
        return new java.sql.Timestamp(System.currentTimeMillis()).toString();
    }
}