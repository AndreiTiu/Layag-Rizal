package model;

// A rider-to-parcel assignment. parcel_id is UNIQUE in the table, so a parcel
// can never be booked by two riders at once. Lifecycle:
//   ASSIGNED -> ACCEPTED -> COMPLETED
//   ASSIGNED -> DECLINED (frees the parcel for a re-assignment attempt)
//   ASSIGNED/ACCEPTED -> FAILED (delivery exception: delay/lost/damaged)
public class ParcelAssignment {
    private int assignmentId;
    private int parcelId;
    private int riderUserId;
    private String status;
    private String reason;
    private String assignedAt;
    private String decidedAt;
    private String completedAt;

    public ParcelAssignment(int assignmentId, int parcelId, int riderUserId, String status,
                            String reason, String assignedAt, String decidedAt, String completedAt) {
        this.assignmentId = assignmentId;
        this.parcelId = parcelId;
        this.riderUserId = riderUserId;
        this.status = status;
        this.reason = reason;
        this.assignedAt = assignedAt;
        this.decidedAt = decidedAt;
        this.completedAt = completedAt;
    }

    public int getAssignmentId() { return assignmentId; }
    public int getParcelId() { return parcelId; }
    public int getRiderUserId() { return riderUserId; }
    public String getStatus() { return status; }
    public String getReason() { return reason; }
    public String getAssignedAt() { return assignedAt; }
    public String getDecidedAt() { return decidedAt; }
    public String getCompletedAt() { return completedAt; }

    public void setStatus(String status) { this.status = status; }
    public void setReason(String reason) { this.reason = reason; }
    public void setDecidedAt(String decidedAt) { this.decidedAt = decidedAt; }
    public void setCompletedAt(String completedAt) { this.completedAt = completedAt; }
}