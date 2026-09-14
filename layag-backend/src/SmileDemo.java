import model.FeeBreakdown;
import repository.*;
import service.*;

public class SmileDemo {
    public static void main(String[] args) throws InterruptedException {
        ParcelRepository parcelRepo = new ParcelRepositoryJdbc();
        StatusHistoryRepository historyRepo = new StatusHistoryRepositoryJdbc();

        ParcelService parcelService = new ParcelService(parcelRepo, new FeeService());
        TrackingService trackingService = new TrackingService(historyRepo);
        FeeService feeService = new FeeService();
        EtaService etaService = new EtaService();

        System.out.println("========== LAYAG 'ALIVE' FEATURES ==========");

        System.out.println("\n--- 1. ITEMIZED FEE RECEIPT (with vehicle) ---");
        FeeBreakdown bd = feeService.computeBreakdown(2.5, "express", "MOTORCYCLE", true);
        System.out.println("  Weight: " + bd.getWeightKg() + " kg x P" + bd.getRatePerKg() + "/kg (" + bd.getTierLabel() + ")");
        System.out.println("  Base fee:                                P" + bd.getBaseFee());
        System.out.println("  x" + bd.getMultiplier() + " (express)");
        System.out.println("  x" + bd.getVehicleMultiplier() + " (motorcycle)");
        System.out.println("  + inter-island surcharge:                P" + bd.getSurcharge());
        System.out.println("  -----------------------------------------------");
        System.out.println("  TOTAL:                                   P" + bd.getTotalFee());

        System.out.println("\n--- 2. ETA CALCULATOR (vehicle affects speed) ---");
        System.out.println("  QC -> Cebu (inter-island, regular, VAN)      : ETA " + etaService.getEstimatedDate(true, "regular", "VAN"));
        System.out.println("  QC -> Cebu (inter-island, express, MOTORCYCLE): ETA " + etaService.getEstimatedDate(true, "express", "MOTORCYCLE"));
        System.out.println("  QC -> Cebu (inter-island, regular, TRUCK)     : ETA " + etaService.getEstimatedDate(true, "regular", "TRUCK"));
        System.out.println("  QC -> Laguna (same-island, regular, VAN)      : ETA " + etaService.getEstimatedDate(false, "regular", "VAN"));

        System.out.println("\n--- 3. AUTO-PILOT (live movement every 1.5s, motorcycle) ---");
        AutoPilot autoPilot = new AutoPilot(parcelService, trackingService, feeService, etaService);
        autoPilot.fly("Demo Sender", "Demo Receiver", 2.5, "express", "MOTORCYCLE", true);

        System.out.println("\n========== DONE ==========");
    }
}