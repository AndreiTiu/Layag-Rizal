package model;

// One transaction for one parcel. COD or ONLINE (mock gateway).
// Status lifecycle: PENDING -> COMPLETED (paid_at set) | FAILED | REFUNDED.
public class Payment {
    private int paymentId;
    private int parcelId;
    private double amount;
    private String method;
    private String status;
    private String reference;
    private String paidAt;
    private String createdAt;

    public Payment(int paymentId, int parcelId, double amount, String method,
                   String status, String reference, String paidAt, String createdAt) {
        this.paymentId = paymentId;
        this.parcelId = parcelId;
        this.amount = amount;
        this.method = method;
        this.status = status;
        this.reference = reference;
        this.paidAt = paidAt;
        this.createdAt = createdAt;
    }

    public int getPaymentId() { return paymentId; }
    public int getParcelId() { return parcelId; }
    public double getAmount() { return amount; }
    public String getMethod() { return method; }
    public String getStatus() { return status; }
    public String getReference() { return reference; }
    public String getPaidAt() { return paidAt; }
    public String getCreatedAt() { return createdAt; }
}