package service;

import java.util.UUID;

// SIMULATED payment gateway (PayMongo / Maya style).
// PRODUCTION REPLACEMENT: this method body becomes an HTTP POST to the real
// gateway API; the gateway would then call our webhook with the result.
// For the capstone we return a reference number ourselves so the full
// transaction flow (authorize -> complete -> receipt) works without a
// merchant account or real money.
public class MockPayGateway {

    // Returns a gateway reference on success, null on (simulated) decline.
    public static String charge(int parcelId, double amount) {
        if (amount <= 0) {
            return null; // gateway would decline a zero charge
        }
        return "PMP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}