package model;

public class StatusEvent {
    private int historyId;
    private int parcelId;
    private String status;
    private String location;
    private String timestamp;
    private String updatedBy;

    public StatusEvent(int historyId, int parcelId, String status, String location, String timestamp, String updatedBy) {
        this.historyId = historyId;
        this.parcelId = parcelId;
        this.status = status;
        this.location = location;
        this.timestamp = timestamp;
        this.updatedBy = updatedBy;
    }

    public int getHistoryId() { return historyId; }
    public int getParcelId() { return parcelId; }
    public String getStatus() { return status; }
    public String getLocation() { return location; }
    public String getTimestamp() { return timestamp; }
    public String getUpdatedBy() { return updatedBy; }
}