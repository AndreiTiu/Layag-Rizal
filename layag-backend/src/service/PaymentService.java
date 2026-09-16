package service;

import model.Payment;
import repository.PaymentRepository;

import java.util.ArrayList;

// Payment business logic.
    // CASH:  row is created PENDING at booking; completed when the rider confirms
    //        the cash collected (auto-triggered on DELIVERED).
    // CARD/EWALLET: row is created PENDING; the mock gateway charges it, then we
    //        mark COMPLETED with the gateway reference. FAILED allows retry.
    // Legacy inputs COD/ONLINE are mapped here so old clients keep working.
    public class PaymentService {
    private PaymentRepository repository;

    public PaymentService(PaymentRepository repository) {
        this.repository = repository;
    }

    public Payment createForParcel(int parcelId, double amount, String method) {
        String norm;
        switch (method == null ? "" : method.toUpperCase()) {
            case "ONLINE":
                norm = "CARD";
                break;
            case "COD":
                norm = "CASH";
                break;
            default:
                norm = method == null || method.isBlank() ? "CASH" : method.toUpperCase();
        }
        return repository.create(parcelId, amount, norm);
    }

    public Payment getByParcel(int parcelId) {
        return repository.findByParcelId(parcelId);
    }

    private boolean isCard(Payment p) {
        return "CARD".equals(p.getMethod()) || "EWALLET".equals(p.getMethod())
            || "ONLINE".equals(p.getMethod());
    }

    private boolean isCash(Payment p) {
        return "CASH".equals(p.getMethod()) || "COD".equals(p.getMethod());
    }

    // Online (card/e-wallet): charge through the (mock) gateway. Idempotent - already paid stays paid.
    public Payment chargeOnline(int parcelId) {
        Payment p = require(parcelId);
        if (!isCard(p)) {
            throw new IllegalArgumentException("Parcel " + parcelId + " is not a card/e-wallet order - nothing to charge online.");
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

    // Cash: rider confirms cash collected at drop-off (called automatically on DELIVERED).
    public Payment confirmCollected(int parcelId) {
        Payment p = require(parcelId);
        if (!isCash(p)) {
            throw new IllegalArgumentException("Parcel " + parcelId + " is not a cash order - use chargeOnline.");
        }
        if ("COMPLETED".equals(p.getStatus())) {
            return p;
        }
        repository.updateStatus(p.getPaymentId(), "COMPLETED", "CASH-" + parcelId);
        return getByParcel(parcelId);
    }

    public ArrayList<Payment> allPayments() {
        return repository.findAll();
    }

    // After an edit changes the fee: re-price the row, only while unpaid.
    public void reprice(int parcelId, double newAmount) {
        Payment p = getByParcel(parcelId);
        if (p != null && "PENDING".equals(p.getStatus())) {
            repository.updateAmount(p.getPaymentId(), newAmount);
        }
    }

    private Payment require(int parcelId) {
        Payment p = getByParcel(parcelId);
        if (p == null) {
            throw new IllegalArgumentException("No payment row exists for parcel " + parcelId);
        }
        return p;
    }
}