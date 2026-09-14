package service;

import model.Parcel;
import model.FeeBreakdown;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class AutoPilot {
    private ParcelService parcelService;
    private TrackingService trackingService;
    private FeeService feeService;
    private EtaService etaService;

    public AutoPilot(ParcelService parcelService, TrackingService trackingService, FeeService feeService, EtaService etaService) {
        this.parcelService = parcelService;
        this.trackingService = trackingService;
        this.feeService = feeService;
        this.etaService = etaService;
    }

    public void fly(String sender, String receiver, double weight, String serviceType, String vehicleType, boolean interIsland) throws InterruptedException {
        System.out.println("[AUTO-PILOT] Registering parcel...");

        Parcel p = parcelService.registerParcel(sender, receiver, weight, serviceType, vehicleType);
        int id = p.getParcelId();
        trackingService.recordInitialStatus(id, "branch_staff");

        FeeBreakdown bd = feeService.computeBreakdown(weight, serviceType, vehicleType, interIsland);
        System.out.println("[AUTO-PILOT] #" + id + " | " + sender + " -> " + receiver
            + " | " + weight + " kg " + serviceType + " " + vehicleType
            + (interIsland ? " (inter-island)" : "") + " | Fee: " + bd.getTotalFee()
            + " | ETA: " + etaService.getEstimatedDate(interIsland, serviceType, vehicleType));

        String[][] route = {
            {"PICKED_UP",          interIsland ? "Quezon City"  : "Quezon City"},
            {"IN_TRANSIT",         interIsland ? "Pasig Hub"    : "Pasig Hub"},
            {"IN_TRANSIT",         interIsland ? "Cebu Hub"     : "Laguna Hub"},
            {"OUT_FOR_DELIVERY",   interIsland ? "Cebu City"    : "Laguna City"},
            {"DELIVERED",          interIsland ? "Cebu City"    : "Laguna City"}
        };

        String current = "REGISTERED";
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm:ss");

        for (String[] step : route) {
            Thread.sleep(1500);
            String nextStatus = step[0];
            String location = step[1];
            trackingService.updateStatus(id, nextStatus, location, "system_autopilot", current);
            parcelService.updateStatus(id, nextStatus);
            current = nextStatus;
            System.out.println("[AUTO-PILOT] " + LocalDateTime.now().format(fmt)
                + "  " + nextStatus + " @ " + location);
        }

        System.out.println("[AUTO-PILOT] Parcel #" + id + " is now " + current + ".");
    }
}