package model;

public class LocationPoint {
    private int locationId;
    private int parcelId;
    private double latitude;
    private double longitude;
    private String recordedAt;

    public LocationPoint(int locationId, int parcelId, double latitude, double longitude, String recordedAt) {
        this.locationId = locationId;
        this.parcelId = parcelId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.recordedAt = recordedAt;
    }

    public int getLocationId() { return locationId; }
    public int getParcelId() { return parcelId; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public String getRecordedAt() { return recordedAt; }
}