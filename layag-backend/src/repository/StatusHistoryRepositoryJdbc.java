package repository;

import model.StatusEvent;
import db.DbConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

public class StatusHistoryRepositoryJdbc implements StatusHistoryRepository {
    @Override
    public void addEvent(int parcelId, String status, String location, String updatedBy) {
        String sql = "INSERT INTO status_history (parcel_id, status, location, updated_by) VALUES (?, ?, ?, ?)";
        try (Connection conn = DbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, parcelId);
            ps.setString(2, status);
            ps.setString(3, location);
            ps.setString(4, updatedBy);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to add status event: " + e.getMessage(), e);
        }
    }

    @Override
    public ArrayList<StatusEvent> getTimeline(int parcelId) {
        ArrayList<StatusEvent> timeline = new ArrayList<>();
        String sql = "SELECT history_id, parcel_id, status, location, update_time, updated_by FROM (" +
                     "  SELECT history_id, parcel_id, status, location, updated_at AS update_time, updated_by FROM status_history WHERE parcel_id = ?" +
                     ") AS t ORDER BY update_time, history_id";
        try (Connection conn = DbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, parcelId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                timeline.add(new StatusEvent(
                    rs.getInt("history_id"),
                    rs.getInt("parcel_id"),
                    rs.getString("status"),
                    rs.getString("location"),
                    rs.getString("update_time"),
                    rs.getString("updated_by")
                ));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load timeline: " + e.getMessage(), e);
        }
        return timeline;
    }
}