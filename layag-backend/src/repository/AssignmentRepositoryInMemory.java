package repository;

import model.ParcelAssignment;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

// In-memory twin of AssignmentRepositoryJdbc. It enforces the same
// "one open assignment per parcel" rule (parcel_id is a map key).
public class AssignmentRepositoryInMemory implements AssignmentRepository {
    private final ConcurrentHashMap<Integer, ParcelAssignment> store = new ConcurrentHashMap<>();
    private final AtomicInteger seq = new AtomicInteger(1);

    @Override
    public ParcelAssignment create(int parcelId, int riderUserId, String reason) {
        if (store.containsKey(parcelId)) {
            throw new RuntimeException("Parcel already has an assignment.");
        }
        ParcelAssignment a = new ParcelAssignment(seq.getAndIncrement(), parcelId, riderUserId, "ASSIGNED", reason, now(), null, null);
        store.put(parcelId, a);
        return a;
    }

    @Override
    public ParcelAssignment findById(int assignmentId) {
        for (ParcelAssignment a : store.values()) {
            if (a.getAssignmentId() == assignmentId) {
                return a;
            }
        }
        return null;
    }

    @Override
    public ParcelAssignment findByParcel(int parcelId) {
        return store.get(parcelId);
    }

    @Override
    public ArrayList<ParcelAssignment> findByRider(int riderUserId, String... statuses) {
        ArrayList<ParcelAssignment> out = new ArrayList<>();
        outer:
        for (ParcelAssignment a : store.values()) {
            if (a.getRiderUserId() != riderUserId) {
                continue;
            }
            for (String s : statuses) {
                if (s.equals(a.getStatus())) {
                    out.add(a);
                    continue outer;
                }
            }
        }
        return out;
    }

    @Override
    public void updateStatus(int assignmentId, String status, String reason) {
        for (ParcelAssignment a : store.values()) {
            if (a.getAssignmentId() == assignmentId) {
                a.setStatus(status);
                a.setReason(reason);
                if (!"ASSIGNED".equals(status)) {
                    a.setDecidedAt(now());
                }
                if ("COMPLETED".equals(status) || "FAILED".equals(status)) {
                    a.setCompletedAt(now());
                }
            }
        }
    }

    @Override
    public void moveTo(int assignmentId, int newRiderUserId, String reason) {
        for (ParcelAssignment a : store.values()) {
            if (a.getAssignmentId() == assignmentId) {
                a.setStatus("ASSIGNED");
                a.setReason(reason);
                a.setDecidedAt(null);
                a.setCompletedAt(null);
                // a.riderUserId needs a re-point: rebuild the value
                store.replace(a.getParcelId(), copy(a, newRiderUserId));
                return;
            }
        }
    }

    private ParcelAssignment copy(ParcelAssignment a, int newRider) {
        ParcelAssignment b = new ParcelAssignment(
            a.getAssignmentId(), a.getParcelId(), newRider, "ASSIGNED", a.getReason(),
            a.getAssignedAt(), null, null);
        return b;
    }

    @Override
    public int countOpen(int riderUserId) {
        int n = 0;
        for (ParcelAssignment a : store.values()) {
            if (a.getRiderUserId() == riderUserId
                    && ("ASSIGNED".equals(a.getStatus()) || "ACCEPTED".equals(a.getStatus()))) {
                n++;
            }
        }
        return n;
    }

    private String now() {
        return new java.sql.Timestamp(System.currentTimeMillis()).toString();
    }
}