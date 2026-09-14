import model.Parcel;
import model.StatusEvent;
import repository.ParcelRepository;
import repository.ParcelRepositoryJdbc;
import repository.StatusHistoryRepository;
import repository.StatusHistoryRepositoryJdbc;
import service.ParcelService;
import service.FeeService;
import service.TrackingService;
import java.util.ArrayList;

public class TrackingTest {
    public static void main(String[] args) {
        ParcelRepository parcelRepo = new ParcelRepositoryJdbc();
        StatusHistoryRepository historyRepo = new StatusHistoryRepositoryJdbc();
        ParcelService parcelService = new ParcelService(parcelRepo, new FeeService());
        TrackingService tracking = new TrackingService(historyRepo);

        Parcel p = parcelService.registerParcel("Alice", "Bob", 1.5, "regular");
        int id = p.getParcelId();
        System.out.println("Registered parcel ID " + id + " (fee " + p.getFee() + ")");
        tracking.recordInitialStatus(id, "branch_staff");

        String current = p.getCurrentStatus();
        walk(tracking, id, current, "PICKED_UP", "Quezon City", "rider_juan");
        walk(tracking, id, "PICKED_UP", "IN_TRANSIT", "Pasig Hub", "rider_juan");
        walk(tracking, id, "IN_TRANSIT", "OUT_FOR_DELIVERY", "Cebu Hub", "courier_ana");
        walk(tracking, id, "OUT_FOR_DELIVERY", "DELIVERED", "Cebu City", "courier_ana");

        System.out.println();
        System.out.println("=== TRY ILLEGAL JUMP: REGISTERED -> DELIVERED ===");
        try {
            tracking.updateStatus(id, "DELIVERED", "Trick", "hacker", "REGISTERED");
        } catch (IllegalStateException e) {
            System.out.println("Blocked (correct): " + e.getMessage());
        }

        System.out.println();
        System.out.println("=== FULL TIMELINE ===");
        ArrayList<StatusEvent> timeline = tracking.getTimeline(id);
        for (StatusEvent ev : timeline) {
            System.out.println("[" + ev.getTimestamp() + "] " + ev.getStatus() + " @ " + ev.getLocation() + " by " + ev.getUpdatedBy());
        }
    }

    static void walk(TrackingService tracking, int id, String from, String to, String location, String who) {
        try {
            tracking.updateStatus(id, to, location, who, from);
            System.out.println("OK: " + from + " -> " + to + " @ " + location);
            from = to;
        } catch (IllegalStateException e) {
            System.out.println("BLOCKED: " + from + " -> " + to + " (" + e.getMessage() + ")");
        }
    }
}