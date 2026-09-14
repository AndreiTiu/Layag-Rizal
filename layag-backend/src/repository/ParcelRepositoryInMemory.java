package repository;

import model.Parcel;
import java.util.ArrayList;

public class ParcelRepositoryInMemory implements ParcelRepository {
    private ArrayList<Parcel> parcels = new ArrayList<>();
    private int nextId = 1;

    @Override
    public Parcel registerParcel(String sender, String receiver, double weight, double fee, String vehicleType, String trackingCode, String serviceType, int createdByUserId) {
        Parcel p = new Parcel(nextId++, sender, receiver, weight, fee, vehicleType, trackingCode);
        p.setServiceType(serviceType);
        p.setCreatedByUserId(createdByUserId);
        parcels.add(p);
        return p;
    }

    @Override
    public Parcel findById(int id) {
        for (Parcel p : parcels) {
            if (p.getParcelId() == id) {
                return p;
            }
        }
        return null;
    }

    @Override
    public Parcel findByTrackingCode(String trackingCode) {
        for (Parcel p : parcels) {
            if (trackingCode != null && trackingCode.equalsIgnoreCase(p.getTrackingCode())) {
                return p;
            }
        }
        return null;
    }

    @Override
    public ArrayList<Parcel> findAll() {
        return parcels;
    }

    @Override
    public ArrayList<Parcel> findByStatus(String status) {
        ArrayList<Parcel> result = new ArrayList<>();
        for (Parcel p : parcels) {
            if (p.getCurrentStatus().equalsIgnoreCase(status)) {
                result.add(p);
            }
        }
        return result;
    }

    @Override
    public ArrayList<Parcel> search(String term) {
        ArrayList<Parcel> result = new ArrayList<>();
        String q = term == null ? "" : term.toLowerCase();
        for (Parcel p : parcels) {
            if (p.getSenderName().toLowerCase().contains(q)
                    || p.getReceiverName().toLowerCase().contains(q)
                    || (p.getTrackingCode() != null && p.getTrackingCode().toLowerCase().contains(q))) {
                result.add(p);
            }
        }
        return result;
    }

    @Override
    public void updateStatus(int id, String status) {
        Parcel p = findById(id);
        if (p != null) {
            p.setCurrentStatus(status);
        }
    }

    @Override
    public int count() {
        return parcels.size();
    }

    @Override
    public long countByStatus(String status) {
        long count = 0;
        for (Parcel p : parcels) {
            if (p.getCurrentStatus().equalsIgnoreCase(status)) {
                count++;
            }
        }
        return count;
    }

    @Override
    public double totalFees() {
        double total = 0.0;
        for (Parcel p : parcels) {
            total += p.getFee();
        }
        return total;
    }

    @Override
    public double averageFee() {
        return parcels.isEmpty() ? 0.0 : totalFees() / parcels.size();
    }
}