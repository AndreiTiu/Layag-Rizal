package repository;

import model.ParcelAssignment;
import java.util.ArrayList;

public interface AssignmentRepository {
    // Unique per parcel: MySQL rejects a second row for the same parcel_id.
    ParcelAssignment create(int parcelId, int riderUserId, String reason);
    ParcelAssignment findById(int assignmentId);
    ParcelAssignment findByParcel(int parcelId);
    // Assignments in "open" states for a rider: statuses like ASSIGNED/ACCEPTED.
    ArrayList<ParcelAssignment> findByRider(int riderUserId, String... statuses);
    void updateStatus(int assignmentId, String status, String reason);
    // Re-point an existing (decided/failed) row at a new rider - the parcel_id
    // is UNIQUE so re-assignment reuses the row instead of inserting a second.
    void moveTo(int assignmentId, int newRiderUserId, String reason);
    // How many parcels this rider currently holds (ASSIGNED or ACCEPTED).
    int countOpen(int riderUserId);
}