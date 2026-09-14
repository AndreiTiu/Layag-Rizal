import model.Parcel;
import model.StatusEvent;
import model.User;
import repository.*;
import service.*;

import java.util.ArrayList;

public class DemoMain {
    public static void main(String[] args) {
        ParcelRepository parcelRepo = new ParcelRepositoryJdbc();
        StatusHistoryRepository historyRepo = new StatusHistoryRepositoryJdbc();
        UserRepository userRepo = new UserRepositoryJdbc();

        ParcelService parcelService = new ParcelService(parcelRepo, new FeeService());
        TrackingService tracking = new TrackingService(historyRepo);
        AuthService auth = new AuthService(userRepo);

        System.out.println("=================== LAYAG DEMO ===================");

        System.out.println("\n--- 1. REGISTER ACCOUNTS (roles) ---");
        try {
            auth.register("Juan Sender", "juan@layag.ph", "pass123", "SENDER");
            System.out.println("  SENDER account created");
        } catch (RuntimeException e) {
            System.out.println("  (already exists)");
        }
        try {
            auth.register("Cora Courier", "cora@layag.ph", "pass123", "COURIER");
            System.out.println("  COURIER account created");
        } catch (RuntimeException e) {
            System.out.println("  (already exists)");
        }

        System.out.println("\n--- 2. SENDER LOGS IN ---");
        User sender = auth.login("juan@layag.ph", "pass123");
        System.out.println("  Logged in as " + sender.getName() + " [" + sender.getRole() + "]");

        System.out.println("\n--- 3. REGISTER PARCEL + FEE ---");
        Parcel p = parcelService.registerParcel(sender.getName(), "Maria Receiver", 2.5, "express");
        tracking.recordInitialStatus(p.getParcelId(), "branch_staff");
        System.out.println("  Tracking #" + p.getParcelId());
        System.out.println("  Weight 2.5 kg x express = P" + p.getFee());
        System.out.println("  Status: " + p.getCurrentStatus());

        System.out.println("\n--- 4. COURIER MOVES PARCEL (valid transitions) ---");
        transition(tracking, parcelService, p, "PICKED_UP", "Quezon City", "cora@layag.ph");
        transition(tracking, parcelService, p, "IN_TRANSIT", "Pasig Hub", "cora@layag.ph");
        transition(tracking, parcelService, p, "OUT_FOR_DELIVERY", "Cebu Hub", "cora@layag.ph");
        transition(tracking, parcelService, p, "DELIVERED", "Cebu City", "cora@layag.ph");

        System.out.println("\n--- 5. RECEIVER TRACKS ---");
        System.out.println("  Tracking #" + p.getParcelId() + " -> " + parcelService.trackParcel(p.getParcelId()).getCurrentStatus());

        System.out.println("\n--- 6. FULL TIMELINE ---");
        ArrayList<StatusEvent> timeline = tracking.getTimeline(p.getParcelId());
        for (StatusEvent ev : timeline) {
            System.out.println("  [" + ev.getTimestamp() + "] " + ev.getStatus() + " @ " + ev.getLocation() + "  by " + ev.getUpdatedBy());
        }

        System.out.println("\n--- 7. ILLEGAL JUMP GUARD ---");
        try {
            transition(tracking, parcelService, p, "IN_TRANSIT", "Trick", "hacker");
        } catch (IllegalStateException e) {
            System.out.println("  Blocked: DELIVERED -> IN_TRANSIT is illegal. " + e.getMessage());
        }

        System.out.println("\n================ DEMO COMPLETE ================");
    }

    static void transition(TrackingService tracking, ParcelService parcelService, Parcel parody, String to, String location, String who) {
        tracking.updateStatus(parody.getParcelId(), to, location, who, parody.getCurrentStatus());
        parcelService.updateStatus(parody.getParcelId(), to);
        System.out.println("  " + parody.getCurrentStatus() + " -> " + to + " @ " + location);
        parody.setCurrentStatus(to);
    }
}