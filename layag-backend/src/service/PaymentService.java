package service;

import model.Payment;
import repository.PaymentRepository;

import java.util.ArrayList;

// Payment business logic.
// COD:    row is created PENDING at booking; completed when the rider confirms
//         the cash collected (auto-triggered on DELIVERED).
// ONLINE: row is created PENDING; the mock gateway charges it, then we mark
//         COMPLETED with the gateway reference. FAILED allows retry.
public class PaymentService {
    private PaymentRepository repository;

    public PaymentService(PaymentRepository repository) {
        this.repository = repository;
    }

    public Payment createForParcel(int parcelId, double amount, String method) {
        String norm = "COD".equalsIgnoreCase(method) || "ONLINE".equalsIgnoreCase(method)
                ? method.toUpperCase() : "COD";
        return repository.create(parcelId, amount, norm);
    }

    public Payment getByParcel(int parcelId) {
        return repository.findByParcelId(parcelId);
    }

    // Online: charge through the (mock) gateway. Idempotent - already paid stays paid.
    public Payment chargeOnline(int parcelId) {
        Payment p = require(parcelId);
        if (!"ONLINE".equals(p.getMethod())) {
            throw new IllegalArgumentException("Parcel " + parcelId + " is a COD order - nothing to charge online.");
        }
        if ("COMPLETED".equals(p.getStatus())) {
            return p;
        }
        String reference = MockPayGateway.charge(parcelId, p.getAmount());
        if (reference == null) {
            repository.updateStatus(p.getPaymentId(), "FAILED", null);
            return getByParcel(parcelId);
        }
        repository.updateStatus(p.getPaymentId(), "COMPLETED", reference);
        return getByParcel(parcelId);
    }

    // COD: rider confirms cash collected at drop-off (called automatically on DELIVERED).
    public Payment confirmCollected(int parcelId) {
        Payment p = require(parcelId);
        if (!"COD".equals(p.getMethod())) {
            throw new IllegalArgumentException("Parcel " + parcelId + " is an ONLINE order - use chargeOnline.");
        }
        if ("COMPLETED".equals(p.getStatus())) {
            return p;
        }
        repository.updateStatus(p.getPaymentId(), "COMPLETED", "COD-" + parcelId);
        return getByParcel(parcelId);
    }

    public ArrayList<Payment> allPayments() {
        return repository.findAll();
    }

    private Payment require(int parcelId) {
        Payment p = getByParcel(parcelId);
        if (p == null) {
            throw new IllegalArgumentException("No payment row exists for parcel " + parcelId);
        }
        return p;
    }
}