package service;

import model.Parcel;
import repository.ParcelRepository;

import java.util.ArrayList;
import java.util.Random;

public class ParcelService {
    private ParcelRepository repository;
    private FeeService feeService;
    // Letters/digits without confusing look-alikes (O/0, I/1).
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    // Business bounds for booking input; anything outside is rejected.
    public static final double MAX_WEIGHT_KG = 200;
    private static final int MAX_NAME_LEN = 100;
    private final Random random = new Random();

    public ParcelService(ParcelRepository repository, FeeService feeService) {
        this.repository = repository;
        this.feeService = feeService;
    }

    public Parcel registerParcel(String sender, String receiver, double weight, String serviceType) {
        return registerParcel(sender, receiver, weight, serviceType, "VAN", false, 0);
    }

    public Parcel registerParcel(String sender, String receiver, double weight, String serviceType, String vehicleType) {
        return registerParcel(sender, receiver, weight, serviceType, vehicleType, false, 0);
    }

    public Parcel registerParcel(String sender, String receiver, double weight, String serviceType, String vehicleType, boolean interIsland) {
        return registerParcel(sender, receiver, weight, serviceType, vehicleType, interIsland, 0);
    }

    // Full path. The stored fee uses the itemized breakdown (incl. inter-island
    // surcharge) so Parcel.fee ALWAYS equals what the receipt shows.
    // createdByUserId links the parcel to the logged-in account that booked it,
    // which is what payment/owner checks are based on.
    public Parcel registerParcel(String sender, String receiver, double weight, String serviceType, String vehicleType, boolean interIsland, int createdByUserId) {
        return registerParcel(sender, receiver, weight, serviceType, vehicleType, interIsland, createdByUserId, null, null, 0);
    }

    // Full path. The stored fee uses the itemized breakdown (incl. distance fee
    // and inter-island surcharge) so Parcel.fee ALWAYS equals what the receipt
    // shows. createdByUserId links the parcel to the logged-in account that
    // booked it, which is what payment/owner checks are based on.
    public Parcel registerParcel(String sender, String receiver, double weight, String serviceType, String vehicleType, boolean interIsland, int createdByUserId,
                                 String pickUpAddress, String dropOffAddress, double distanceKm) {
        // Input bounds: reject nonsense here, before it reaches the fee engine
        // or the database. The API layer checks format; this is the business
        // layer checking rules (defense in depth).
        if (sender == null || sender.isBlank()) {
            throw new IllegalArgumentException("Sender name is required.");
        }
        if (receiver == null || receiver.isBlank()) {
            throw new IllegalArgumentException("Receiver name is required.");
        }
        String send = sender.trim();
        String recv = receiver.trim();
        if (send.length() > MAX_NAME_LEN || recv.length() > MAX_NAME_LEN) {
            throw new IllegalArgumentException("Names must be " + MAX_NAME_LEN + " characters or fewer.");
        }
        if (weight <= 0 || weight > MAX_WEIGHT_KG) {
            throw new IllegalArgumentException("Weight must be between 0 and " + MAX_WEIGHT_KG + " kg.");
        }
        if (distanceKm < 0) {
            throw new IllegalArgumentException("Distance cannot be negative.");
        }
        if (serviceType == null || serviceType.isBlank()) {
            throw new IllegalArgumentException("Service type is required.");
        }
        double fee = feeService.computeBreakdown(weight, serviceType, vehicleType, interIsland, distanceKm).getTotalFee();
        return repository.registerParcel(send, recv, weight, fee, vehicleType, generateTrackingCode(), serviceType, createdByUserId, pickUpAddress, dropOffAddress, distanceKm, interIsland);
    }

    // Sender's own deliveries ("My bookings" screen).
    public ArrayList<Parcel> parcelsByOwner(int userId) {
        return repository.findByCreatedBy(userId);
    }

    // Owner confirms the parcel details/weight before assignment.
    public void confirmParcel(int parcelId) {
        repository.confirm(parcelId);
    }

    // Edit before assignment: caller supplies only the fields that changed.
    // Fee is recomputed from whatever ends up as the final values (weight,
    // service, vehicle, distance), and the result is returned.
    public Parcel editParcel(int parcelId, String sender, String receiver, String pickUpAddress, String dropOffAddress,
                             Double newWeight, String serviceType, String vehicleType, Boolean interIsland, Double distanceKm) {
        Parcel p = repository.findById(parcelId);
        if (p == null) {
            throw new IllegalArgumentException("Parcel not found.");
        }
        String finalSender = sender == null || sender.isBlank() ? p.getSenderName() : sender.trim();
        String finalReceiver = receiver == null || receiver.isBlank() ? p.getReceiverName() : receiver.trim();
        String finalPickUp = pickUpAddress == null || pickUpAddress.isBlank() ? p.getPickUpAddress() : pickUpAddress.trim();
        String finalDropOff = dropOffAddress == null || dropOffAddress.isBlank() ? p.getDropOffAddress() : dropOffAddress.trim();
        double finalWeight = newWeight == null ? p.getWeightKg() : newWeight;
        String finalService = serviceType == null || serviceType.isBlank() ? p.getServiceType() : serviceType;
        String finalVehicle = vehicleType == null || vehicleType.isBlank() ? p.getVehicleType() : vehicleType;
        double finalDistance = distanceKm == null ? p.getDistanceKm() : distanceKm;
        if (finalWeight <= 0 || finalWeight > MAX_WEIGHT_KG) {
            throw new IllegalArgumentException("Weight must be between 0 and " + MAX_WEIGHT_KG + " kg.");
        }
        boolean island = interIsland != null ? interIsland : p.isInterIsland();
        double fee = feeService.computeBreakdown(finalWeight, finalService, finalVehicle, island, finalDistance).getTotalFee();
        repository.updateDetails(parcelId, finalSender, finalReceiver, finalPickUp, finalDropOff, finalWeight, fee, finalVehicle, finalService, finalDistance, island);
        return repository.findById(parcelId);
    }

    // Public code like LAYAG-D7K3-9P2M-5Q8X that the sender receives at booking.
    // Decoupled from the internal DB id; guaranteed unique via DB index + random generation.
    private String generateTrackingCode() {
        StringBuilder sb = new StringBuilder("LAYAG-");
        for (int i = 0; i < 12; i++) {
            if (i == 4 || i == 8) {
                sb.append('-');
            }
            sb.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
        }
        return sb.toString();
    }

    public void updateStatus(int parcelId, String status) {
        repository.updateStatus(parcelId, status);
    }

    public Parcel trackParcel(int parcelId) {
        return repository.findById(parcelId);
    }

    public Parcel trackByCode(String trackingCode) {
        return repository.findByTrackingCode(trackingCode);
    }

    public ArrayList<Parcel> searchParcels(String term) {
        return repository.search(term);
    }

    public ArrayList<Parcel> parcelsByStatus(String status) {
        return repository.findByStatus(status);
    }
}