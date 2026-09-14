package model;

public class EmergencyRequest {
    private final String requestId;
    private final Location emergencyLocation;
    private final Severity severity;
    private final String description;

    public EmergencyRequest(
            String requestId,
            Location emergencyLocation,
            Severity severity,
            String description) {

        this.requestId = requestId;
        this.emergencyLocation = emergencyLocation;
        this.severity = severity;
        this.description = description;
    }

    public String getRequestId() {
        return requestId;
    }

    public Location getEmergencyLocation() {
        return emergencyLocation;
    }

    public Severity getSeverity() {
        return severity;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return "EmergencyRequest{" +
                "requestId='" + requestId + '\'' +
                ", location=" + emergencyLocation.getName() +
                ", severity=" + severity +
                ", description='" + description + '\'' +
                '}';
    }
}