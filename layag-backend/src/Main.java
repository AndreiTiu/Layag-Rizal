import model.Parcel;
import repository.ParcelRepository;
import repository.ParcelRepositoryInMemory;
import service.ParcelService;
import service.FeeService;

public class Main {
    public static void main(String[] args) {
        ParcelRepository repo = new ParcelRepositoryInMemory();
        ParcelService service = new ParcelService(repo, new FeeService());

        Parcel p1 = service.registerParcel("Alice", "Bob", 1.5, "regular");
        Parcel p2 = service.registerParcel("Charlie", "David", 2.0, "express");
        Parcel p3 = service.registerParcel("Eve", "Frank", 0.75, "regular");
        Parcel p4 = service.registerParcel("Grace", "Heidi", 3.0, "same-day");

        System.out.println("Total parcels registered: " + repo.count());
        System.out.println();

        System.out.println("=====ALL PARCEL=====");
        for (Parcel p : repo.findAll()) {
            System.out.println("Parcel ID: " + p.getParcelId() + " | " + p.getSenderName() + " -> " + p.getReceiverName() + " | Weight: " + p.getWeightKg() + "kg | Fee: " + p.getFee() + " | Status: " + p.getCurrentStatus());
        }
        System.out.println();

        Parcel found = service.trackParcel(2);
        if (found != null) {
            System.out.println("Found ID 2: " + found.getSenderName() + " -> " + found.getReceiverName() + " | Fee: " + found.getFee());
        } else {
            System.out.println("ID 2 not found");
        }

        service.updateStatus(2, "OUT_FOR_DELIVERY");
        System.out.println("Parcel 2 status updated: " + service.trackParcel(2).getCurrentStatus());
    }
}