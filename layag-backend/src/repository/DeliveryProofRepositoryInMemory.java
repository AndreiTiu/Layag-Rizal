package repository;

import model.DeliveryProof;
import java.util.concurrent.ConcurrentHashMap;

// In-memory twin of DeliveryProofRepositoryJdbc.
public class DeliveryProofRepositoryInMemory implements DeliveryProofRepository {
    private final ConcurrentHashMap<Integer, DeliveryProof> store = new ConcurrentHashMap<>();
    private int seq = 1;

    @Override
    public DeliveryProof upsert(int parcelId, int riderUserId, String photoName, String notes, String recipientName) {
        DeliveryProof p = new DeliveryProof(seq++, parcelId, riderUserId, photoName, notes, recipientName, now());
        store.put(parcelId, p);
        return p;
    }

    @Override
    public DeliveryProof findByParcel(int parcelId) {
        return store.get(parcelId);
    }

    private String now() {
        return new java.sql.Timestamp(System.currentTimeMillis()).toString();
    }
}