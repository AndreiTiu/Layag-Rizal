package repository;

import model.LocationPoint;
import db.DbConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

public class GpsRepositoryJdbc implements GpsRepository {
    @Override
    public void recordLocation(int parcelId, double latitude, double longitude) {
        String sql = "INSERT INTO locations (parcel_id, latitude, longitude) VALUES (?, ?, ?)";
        try (Connection conn = DbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, parcelId);
            ps.setDouble(2, latitude);
            ps.setDouble(3, longitude);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to record location: " + e.getMessage(), e);
        }
    }

    @Override
    public ArrayList<LocationPoint> getRoute(int parcelId) {
        ArrayList<LocationPoint> route = new ArrayList<>();
        String sql = "SELECT location_id, parcel_id, latitude, longitude, recorded_at FROM locations WHERE parcel_id = ? ORDER BY recorded_at, location_id";
        try (Connection conn = DbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, parcelId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                route.add(new LocationPoint(
                    rs.getInt("location_id"),
                    rs.getInt("parcel_id"),
                    rs.getDouble("latitude"),
                    rs.getDouble("longitude"),
                    rs.getString("recorded_at")
                ));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load route: " + e.getMessage(), e);
        }
        return route;
    }
}