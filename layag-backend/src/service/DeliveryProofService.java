package service;

import model.DeliveryProof;
import repository.DeliveryProofRepository;

// Proof-of-delivery metadata. Photo bytes are written to disk by API layer;
// this service owns the DB record (one per parcel, re-upload overwrites).
public class DeliveryProofService {
    private final DeliveryProofRepository repository;

    public DeliveryProofService(DeliveryProofRepository repository) {
        this.repository = repository;
    }

    public DeliveryProof save(int parcelId, int riderUserId, String photoName, String notes, String recipientName) {
        if (photoName == null || photoName.isBlank()) {
            throw new IllegalArgumentException("A photo is required for the delivery proof.");
        }
        String text = (notes == null || notes.isBlank()) ? null : notes.trim();
        String recv = (recipientName == null || recipientName.isBlank()) ? null : recipientName.trim();
        return repository.upsert(parcelId, riderUserId, photoName, text, recv);
    }

    public DeliveryProof byParcel(int parcelId) {
        return repository.findByParcel(parcelId);
    }
}