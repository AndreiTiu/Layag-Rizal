package repository;

import model.LocationPoint;
import java.util.ArrayList;

public interface GpsRepository {
    void recordLocation(int parcelId, double latitude, double longitude);
    ArrayList<LocationPoint> getRoute(int parcelId);
}