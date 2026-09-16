package model;

public class Parcel {
    private int parcelId;
    private String senderName;
    private String receiverName;
    private double weightKg;
    private String currentStatus;
    private double fee;
    private String vehicleType;
    private String trackingCode;
    private String serviceType;
    private int createdByUserId;
    private String pickUpAddress;
    private String dropOffAddress;
    private double distanceKm;
    private boolean interIsland;
    private boolean confirmed;

    public Parcel(int parcelId, String senderName, String receiverName, double weightKg, double fee) {
        this(parcelId, senderName, receiverName, weightKg, fee, "VAN", null, null, null, 0, false, false);
    }

    public Parcel(int parcelId, String senderName, String receiverName, double weightKg, double fee, String vehicleType) {
        this(parcelId, senderName, receiverName, weightKg, fee, vehicleType, null, null, null, 0, false, false);
    }

    public Parcel(int parcelId, String senderName, String receiverName, double weightKg, double fee, String vehicleType, String trackingCode) {
        this(parcelId, senderName, receiverName, weightKg, fee, vehicleType, trackingCode, null, null, 0, false, false);
    }

    public Parcel(int parcelId, String senderName, String receiverName, double weightKg, double fee, String vehicleType, String trackingCode,
                  String pickUpAddress, String dropOffAddress, double distanceKm, boolean interIsland, boolean confirmed) {
        this.parcelId = parcelId;
        this.senderName = senderName;
        this.receiverName = receiverName;
        this.weightKg = weightKg;
        this.currentStatus = "REGISTERED";
        this.fee = fee;
        this.vehicleType = vehicleType;
        this.trackingCode = trackingCode;
        this.serviceType = "regular";
        this.pickUpAddress = pickUpAddress;
        this.dropOffAddress = dropOffAddress;
        this.distanceKm = distanceKm;
        this.interIsland = interIsland;
        this.confirmed = confirmed;
    }

    public int getParcelId() { return parcelId; }
    public String getSenderName() { return senderName; }
    public String getReceiverName() { return receiverName; }
    public double getWeightKg() { return weightKg; }
    public String getCurrentStatus() { return currentStatus; }
    public double getFee() { return fee; }
    public String getVehicleType() { return vehicleType; }
    public String getTrackingCode() { return trackingCode; }
    public String getServiceType() { return serviceType; }
    public int getCreatedByUserId() { return createdByUserId; }
    public String getPickUpAddress() { return pickUpAddress; }
    public String getDropOffAddress() { return dropOffAddress; }
    public double getDistanceKm() { return distanceKm; }
    public boolean isInterIsland() { return interIsland; }
    public boolean isConfirmed() { return confirmed; }

    public void setCreatedByUserId(int createdByUserId) { this.createdByUserId = createdByUserId; }

    public void setCurrentStatus(String currentStatus) { this.currentStatus = currentStatus; }
    public void setFee(double fee) { this.fee = fee; }
    public void setTrackingCode(String trackingCode) { this.trackingCode = trackingCode; }
    public void setServiceType(String serviceType) { this.serviceType = serviceType; }
    public void setSenderName(String senderName) { this.senderName = senderName; }
    public void setReceiverName(String receiverName) { this.receiverName = receiverName; }
    public void setWeightKg(double weightKg) { this.weightKg = weightKg; }
    public void setVehicleType(String vehicleType) { this.vehicleType = vehicleType; }
    public void setPickUpAddress(String pickUpAddress) { this.pickUpAddress = pickUpAddress; }
    public void setDropOffAddress(String dropOffAddress) { this.dropOffAddress = dropOffAddress; }
    public void setDistanceKm(double distanceKm) { this.distanceKm = distanceKm; }
    public void setInterIsland(boolean interIsland) { this.interIsland = interIsland; }
    public void setConfirmed(boolean confirmed) { this.confirmed = confirmed; }
}