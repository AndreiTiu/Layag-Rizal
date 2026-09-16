package service;

import model.Parcel;
import model.ParcelAssignment;
import model.RiderProfile;
import repository.AssignmentRepository;
import repository.RatingRepository;
import repository.RiderRepository;

import java.util.ArrayList;
import java.util.Comparator;

// Assignment domain logic.
//
// Trusted-role model: riders are PROVISIONED by an admin (a public sign-up
// can never claim COURIER). Once a parcel is CONFIRMED, the system picks the
// best available rider from autoAssign(). A declined/failed parcel is offered
// to the next best rider (reassign). parcel_id is UNIQUE in the table, so
// re-assignments REUSE the existing row (moveTo) instead of inserting a
// second one - that is what prevents double-booking.
public class AssignmentService {
    // Scoring weights for "best fit". Exact vehicle match is the strongest
    // signal (a motorcycle rider shouldn't get a truck-sized parcel when a van
    // rider is free); free capacity is the tie-breaker.
    private static final int VEHICLE_MATCH_BONUS = 10;
    private static final int FREE_CAPACITY_WEIGHT = 3;

    private final RiderRepository riderRepository;
    private final AssignmentRepository assignmentRepository;
    private final RatingRepository ratingRepository;

    public AssignmentService(RiderRepository riderRepository, AssignmentRepository assignmentRepository,
                             RatingRepository ratingRepository) {
        this.riderRepository = riderRepository;
        this.assignmentRepository = assignmentRepository;
        this.ratingRepository = ratingRepository;
    }

    // Admin creates the rider profile row for an existing COURIER account.
    public RiderProfile provision(int riderUserId, String vehicleType, int maxConcurrent) {
        String vehicle = vehicleType == null || vehicleType.isBlank() ? "MOTORCYCLE" : vehicleType.toUpperCase();
        int cap = maxConcurrent <= 0 ? 3 : maxConcurrent;
        riderRepository.createProfile(riderUserId, vehicle, cap);
        return riderRepository.findById(riderUserId);
    }

    // All riders with their current load attached (for the admin roster).
    public ArrayList<RiderProfile> roster() {
        ArrayList<RiderProfile> all = riderRepository.findAll();
        for (RiderProfile r : all) {
            attachStats(r);
        }
        all.sort(Comparator.comparingInt(RiderProfile::getRiderUserId));
        return all;
    }

    public RiderProfile profile(int riderUserId) {
        RiderProfile p = riderRepository.findById(riderUserId);
        if (p == null) {
            return null;
        }
        attachStats(p);
        return p;
    }

    // Load (open assignments) + reputation (ratings) in one place so every
    // "rider card" in the API shows the same numbers.
    private void attachStats(RiderProfile p) {
        p.setCurrentLoad(assignmentRepository.countOpen(p.getRiderUserId()));
        p.setAvgRating(ratingRepository.averageForRider(p.getRiderUserId()));
        p.setRatingCount(ratingRepository.findByRider(p.getRiderUserId()).size());
    }

    public void setAvailability(int riderUserId, boolean available) {
        riderRepository.setAvailable(riderUserId, available);
    }

    // Ranks available riders for a parcel. Only riders who are on-shift
    // (is_available) and below capacity qualify.
    private ArrayList<RiderProfile> candidates(Parcel p, Integer excludeRider) {
        ArrayList<RiderProfile> out = new ArrayList<>();
        for (RiderProfile r : roster()) {
            if (!r.isAvailable() || r.getCurrentLoad() >= r.getMaxConcurrent()) {
                continue;
            }
            if (excludeRider != null && r.getRiderUserId() == excludeRider) {
                continue;
            }
            out.add(r);
        }
        return out;
    }

    private RiderProfile bestFit(Parcel p, Integer excludeRider) {
        final String need = p.getVehicleType() == null ? "VAN" : p.getVehicleType().toUpperCase();
        RiderProfile best = null;
        int bestScore = Integer.MIN_VALUE;
        for (RiderProfile r : candidates(p, excludeRider)) {
            int score = 0;
            if (need.equals(r.getVehicleType().toUpperCase())) {
                score += VEHICLE_MATCH_BONUS;
            }
            score += (r.getMaxConcurrent() - r.getCurrentLoad()) * FREE_CAPACITY_WEIGHT;
            if (score > bestScore) {
                bestScore = score;
                best = r;
            }
        }
        return best;
    }

    // Auto-assign the single best rider to a CONFIRMED parcel. Returns the new
    // assignment, or null when no eligible rider is on shift (admin is then
    // expected to assign manually). Never double-books: an open assignment
    // keeps the parcel; a decided/failed parcel is offered to the next rider.
    public ParcelAssignment autoAssign(Parcel p) {
        ParcelAssignment existing = assignmentRepository.findByParcel(p.getParcelId());
        if (existing == null) {
            RiderProfile best = bestFit(p, null);
            return best == null ? null : assignmentRepository.create(p.getParcelId(), best.getRiderUserId(), "AUTO");
        }
        if ("ASSIGNED".equals(existing.getStatus()) || "ACCEPTED".equals(existing.getStatus())) {
            return null; // already assigned, do nothing
        }
        return reassign(p, existing.getRiderUserId());
    }

    // Offer a decided/failed/freed parcel to the next best rider, excluding the
    // previous one. Reuses the existing UNIQUE row (moveTo) on purpose.
    public ParcelAssignment reassign(Parcel p, Integer excludeRider) {
        RiderProfile best = bestFit(p, excludeRider);
        if (best == null) {
            return null;
        }
        ParcelAssignment existing = assignmentRepository.findByParcel(p.getParcelId());
        if (existing == null) {
            return assignmentRepository.create(p.getParcelId(), best.getRiderUserId(), "AUTO");
        }
        assignmentRepository.moveTo(existing.getAssignmentId(), best.getRiderUserId(), "REASSIGN");
        return assignmentRepository.findByParcel(p.getParcelId());
    }

    // Manual push by admin (also recovers parcels no rider auto-assigned).
    public ParcelAssignment assign(int parcelId, int riderUserId) {
        ParcelAssignment existing = assignmentRepository.findByParcel(parcelId);
        if (existing != null
                && ("ASSIGNED".equals(existing.getStatus()) || "ACCEPTED".equals(existing.getStatus()))) {
            throw new IllegalArgumentException("Parcel is already assigned to rider " + existing.getRiderUserId() + ".");
        }
        if (riderRepository.findById(riderUserId) == null) {
            throw new IllegalArgumentException("No rider profile for user " + riderUserId + ".");
        }
        if (existing == null) {
            return assignmentRepository.create(parcelId, riderUserId, "ADMIN");
        }
        assignmentRepository.moveTo(existing.getAssignmentId(), riderUserId, "ADMIN");
        return assignmentRepository.findByParcel(parcelId);
    }

    // Rider accepts a job from their work queue.
    public ParcelAssignment accept(int assignmentId, int riderUserId) {
        ParcelAssignment a = owned(assignmentId, riderUserId);
        if (!"ASSIGNED".equals(a.getStatus())) {
            throw new IllegalArgumentException("Only an ASSIGNED job can be accepted (current: " + a.getStatus() + ").");
        }
        assignmentRepository.updateStatus(assignmentId, "ACCEPTED", "rider accepted");
        return assignmentRepository.findById(assignmentId);
    }

    // Rider turns a job down; the parcel is immediately offered to the next
    // best available rider (excluding the one who declined).
    public ParcelAssignment decline(int assignmentId, int riderUserId, String reason) {
        ParcelAssignment a = owned(assignmentId, riderUserId);
        if (!"ASSIGNED".equals(a.getStatus())) {
            throw new IllegalArgumentException("Only an ASSIGNED job can be declined (current: " + a.getStatus() + ").");
        }
        assignmentRepository.updateStatus(assignmentId, "DECLINED",
            reason == null || reason.isBlank() ? "rider declined" : reason);
        return assignmentRepository.findById(assignmentId);
    }

    // True when this rider can act on the parcel's delivery status.
    public boolean isAssignedTo(int parcelId, int riderUserId) {
        ParcelAssignment a = assignmentRepository.findByParcel(parcelId);
        return a != null
            && a.getRiderUserId() == riderUserId
            && !"DECLINED".equals(a.getStatus())
            && !"FAILED".equals(a.getStatus());
    }

    // Called by the status pipeline when a parcel reaches a terminal state
    // (DELIVERED = rider earnings; FAILED/RETURNED/CANCELLED = close the slot).
    public void closeForParcel(int parcelId, String terminalStatus) {
        ParcelAssignment a = assignmentRepository.findByParcel(parcelId);
        if (a == null) {
            return;
        }
        if ("ASSIGNED".equals(a.getStatus()) || "ACCEPTED".equals(a.getStatus())) {
            assignmentRepository.updateStatus(a.getAssignmentId(),
                "DELIVERED".equals(terminalStatus) ? "COMPLETED" : "FAILED",
                "parcel " + terminalStatus.toLowerCase());
        }
    }

    public ArrayList<ParcelAssignment> riderJobs(int riderUserId, String... statuses) {
        return assignmentRepository.findByRider(riderUserId, statuses);
    }

    public ParcelAssignment byParcel(int parcelId) {
        return assignmentRepository.findByParcel(parcelId);
    }

    private ParcelAssignment owned(int assignmentId, int riderUserId) {
        ParcelAssignment a = assignmentRepository.findById(assignmentId);
        if (a == null) {
            throw new IllegalArgumentException("Assignment not found.");
        }
        if (a.getRiderUserId() != riderUserId) {
            throw new IllegalArgumentException("This job is not assigned to you.");
        }
        return a;
    }
}