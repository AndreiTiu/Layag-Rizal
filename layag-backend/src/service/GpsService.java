package service;

import model.LocationPoint;
import repository.GpsRepository;

import java.util.ArrayList;

public class GpsService {
    private GpsRepository gpsRepository;

    public GpsService(GpsRepository gpsRepository) {
        this.gpsRepository = gpsRepository;
    }

    public void recordLocation(int parcelId, double latitude, double longitude) {
        gpsRepository.recordLocation(parcelId, latitude, longitude);
    }

    public ArrayList<LocationPoint> getRoute(int parcelId) {
        return gpsRepository.getRoute(parcelId);
    }

    // Distance between two GPS points in km (Haversine formula).
    // Used to display "x km from pickup point" or validate pings.
    public double distanceKm(double lat1, double lng1, double lat2, double lng2) {
        double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                 * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * R * Math.asin(Math.sqrt(a));
    }
}