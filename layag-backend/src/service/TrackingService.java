package service;

import model.StatusEvent;
import repository.StatusHistoryRepository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class TrackingService {
    private StatusHistoryRepository historyRepo;
    private Map<String, ArrayList<String>> validTransitions;

    public TrackingService(StatusHistoryRepository historyRepo) {
        this.historyRepo = historyRepo;
        this.validTransitions = buildValidTransitions();
    }

    // CORE ALGORITHM: only legal status jumps are allowed.
    // REGISTERED -> PICKED_UP -> IN_TRANSIT -> OUT_FOR_DELIVERY -> DELIVERED
    // Terminals: DELIVERED, FAILED, RETURNED, CANCELLED.
    // REGISTERED -> FAILED also allowed; CANCELLED is the customer-initiated exit.
    private Map<String, ArrayList<String>> buildValidTransitions() {
        Map<String, ArrayList<String>> map = new HashMap<>();
        map.put("REGISTERED",       new ArrayList<>(Arrays.asList("PICKED_UP", "FAILED", "CANCELLED")));
        map.put("PICKED_UP",        new ArrayList<>(Arrays.asList("IN_TRANSIT")));
        map.put("IN_TRANSIT",       new ArrayList<>(Arrays.asList("IN_TRANSIT", "OUT_FOR_DELIVERY", "FAILED")));
        map.put("OUT_FOR_DELIVERY", new ArrayList<>(Arrays.asList("DELIVERED", "FAILED", "RETURNED")));
        return map;
    }

    // Non-transition lifecycle events that land on the timeline WITHOUT moving
    // the parcel to a new machine state (confirm / reschedule / edit / address
    // change). Unrestricted by the state machine by design.
    public void recordEvent(int parcelId, String event, String location, String updatedBy) {
        historyRepo.addEvent(parcelId, event, location, updatedBy);
    }

    public boolean canTransition(String from, String to) {
        ArrayList<String> allowed = validTransitions.get(from);
        return allowed != null && allowed.contains(to);
    }

    public void updateStatus(int parcelId, String status, String location, String updatedBy, String currentStatus) {
        // Must never jump from a terminal state
        if (isTerminal(currentStatus)) {
            throw new IllegalStateException("Parcel is already " + currentStatus + " — cannot update.");
        }
        if (!canTransition(currentStatus, status)) {
            throw new IllegalStateException("Illegal transition: " + currentStatus + " -> " + status);
        }
        historyRepo.addEvent(parcelId, status, location, updatedBy);
    }

    public void recordInitialStatus(int parcelId, String updatedBy) {
        historyRepo.addEvent(parcelId, "REGISTERED", "Registration", updatedBy);
    }

    private boolean isTerminal(String status) {
        return status.equals("DELIVERED") || status.equals("FAILED") || status.equals("RETURNED") || status.equals("CANCELLED");
    }

    public ArrayList<StatusEvent> getTimeline(int parcelId) {
        return historyRepo.getTimeline(parcelId);
    }
}