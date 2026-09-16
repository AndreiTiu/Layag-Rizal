package repository;

import model.DeliveryProof;

public interface DeliveryProofRepository {
    // One proof per parcel - a re-upload updates the row + file name.
    DeliveryProof upsert(int parcelId, int riderUserId, String photoName, String notes, String recipientName);
    DeliveryProof findByParcel(int parcelId);
}