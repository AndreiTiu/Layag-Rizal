package repository;

import model.Payment;
import java.util.ArrayList;

public interface PaymentRepository {
    Payment create(int parcelId, double amount, String method);
    Payment findByParcelId(int parcelId);
    ArrayList<Payment> findAll();
    // Sets status and (optional) gateway reference; paid_at is set when status = COMPLETED.
    void updateStatus(int paymentId, String status, String reference);
}