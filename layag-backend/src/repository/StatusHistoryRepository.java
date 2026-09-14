package repository;

import model.StatusEvent;
import java.util.ArrayList;

public interface StatusHistoryRepository {
    void addEvent(int parcelId, String status, String location, String updatedBy);
    ArrayList<StatusEvent> getTimeline(int parcelId);
}