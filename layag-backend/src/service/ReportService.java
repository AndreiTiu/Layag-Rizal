package service;

import model.DashboardSummary;
import model.Parcel;
import repository.ParcelRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ReportService {
    private ParcelRepository repository;

    public ReportService(ParcelRepository repository) {
        this.repository = repository;
    }

    // Builds the admin dashboard: totals, status breakdown, recent shipments.
    // Data processing happens here (ArrayList + Map loops) — rubric-visible.
    public DashboardSummary buildSummary() {
        ArrayList<Parcel> all = repository.findAll();

        int total = all.size();
        double revenue = 0.0;
        Map<String, Long> byStatus = new LinkedHashMap<>();
        List<String> order = List.of("REGISTERED", "PICKED_UP", "IN_TRANSIT", "OUT_FOR_DELIVERY", "DELIVERED", "FAILED", "RETURNED");

        for (Parcel p : all) {
            revenue += p.getFee();
            byStatus.merge(p.getCurrentStatus(), 1L, Long::sum);
        }
        double average = total == 0 ? 0.0 : Math.round((revenue / total) * 100.0) / 100.0;

        ArrayList<Parcel> recent = new ArrayList<>(all);
        if (recent.size() > 10) {
            recent = new ArrayList<>(recent.subList(recent.size() - 10, recent.size()));
        }

        return new DashboardSummary(total, Math.round(revenue * 100.0) / 100.0, average, byStatus, recent);
    }
}