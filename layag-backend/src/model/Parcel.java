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

    public Parcel(int parcelId, String senderName, String receiverName, double weightKg, double fee) {
        this(parcelId, senderName, receiverName, weightKg, fee, "VAN", null);
    }

    public Parcel(int parcelId, String senderName, String receiverName, double weightKg, double fee, String vehicleType) {
        this(parcelId, senderName, receiverName, weightKg, fee, vehicleType, null);
    }

    public Parcel(int parcelId, String senderName, String receiverName, double weightKg, double fee, String vehicleType, String trackingCode) {
        this.parcelId = parcelId;
        this.senderName = senderName;
        this.receiverName = receiverName;
        this.weightKg = weightKg;
        this.currentStatus = "REGISTERED";
        this.fee = fee;
        this.vehicleType = vehicleType;
        this.trackingCode = trackingCode;
        this.serviceType = "regular";
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

    public void setCreatedByUserId(int createdByUserId) { this.createdByUserId = createdByUserId; }

    public void setCurrentStatus(String currentStatus) { this.currentStatus = currentStatus; }
    public void setFee(double fee) { this.fee = fee; }
    public void setTrackingCode(String trackingCode) { this.trackingCode = trackingCode; }
    public void setServiceType(String serviceType) { this.serviceType = serviceType; }
}