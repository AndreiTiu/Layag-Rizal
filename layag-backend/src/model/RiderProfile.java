package model;

// One row per courier account. Availability + capacity are read by the
// auto-assignment algorithm to pick the best rider for a parcel.
public class RiderProfile {
    private int riderUserId;
    private String vehicleType;
    private boolean isAvailable;
    private int maxConcurrent;

    // Transient (computed at query time, not stored): how many parcels the
    // rider currently holds (ASSIGNED + ACCEPTED assignments).
    private int currentLoad;

    // Transient: rider reputation from the ratings table.
    private double averageRating;
    private int ratingCount;

    public RiderProfile(int riderUserId, String vehicleType, boolean isAvailable, int maxConcurrent) {
        this.riderUserId = riderUserId;
        this.vehicleType = vehicleType;
        this.isAvailable = isAvailable;
        this.maxConcurrent = maxConcurrent;
    }

    public String getVehicleType() { return vehicleType; }
    public int getRiderUserId() { return riderUserId; }
    public boolean isAvailable() { return isAvailable; }
    public int getMaxConcurrent() { return maxConcurrent; }
    public int getCurrentLoad() { return currentLoad; }
    public int getOpenCapacity() { return Math.max(0, currentLoad); }

    public void setCurrentLoad(int currentLoad) { this.currentLoad = currentLoad; }
    public void setAvgRating(double averageRating) { this.averageRating = averageRating; }
    public void setRatingCount(int ratingCount) { this.ratingCount = ratingCount; }
    public double getAverageRating() { return averageRating; }
    public int getRatingCount() { return ratingCount; }
    public void setAvailable(boolean isAvailable) { this.isAvailable = isAvailable; }
    public void setVehicleType(String vehicleType) { this.vehicleType = vehicleType; }
    public void setMaxConcurrent(int maxConcurrent) { this.maxConcurrent = maxConcurrent; }
}