package model;

// Photograph + metadata proving a delivery was dropped off.
public class DeliveryProof {
    private int proofId;
    private int parcelId;
    private int riderUserId;
    private String photoName;   // file on disk, served via /api/proofs/photo
    private String notes;
    private String recipientName;
    private String deliveredAt;

    public DeliveryProof(int proofId, int parcelId, int riderUserId, String photoName,
                         String notes, String recipientName, String deliveredAt) {
        this.proofId = proofId;
        this.parcelId = parcelId;
        this.riderUserId = riderUserId;
        this.photoName = photoName;
        this.notes = notes;
        this.recipientName = recipientName;
        this.deliveredAt = deliveredAt;
    }

    public int getProofId() { return proofId; }
    public int getParcelId() { return parcelId; }
    public int getRiderUserId() { return riderUserId; }
    public String getPhotoName() { return photoName; }
    public String getNotes() { return notes; }
    public String getRecipientName() { return recipientName; }
    public String getDeliveredAt() { return deliveredAt; }
}