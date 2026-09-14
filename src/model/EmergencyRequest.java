package model;

public class EmergencyRequest implements Comparable<EmergencyRequest> {
    private final String requestId;
    private final String patientName;
    private final Location emergencyLocation;
    private final Severity severity;

    public EmergencyRequest(
            String requestId,
            String patientName,
            Location emergencyLocation,
            Severity severity) {

        this.requestId = requestId;
        this.patientName = patientName;
        this.emergencyLocation = emergencyLocation;
        this.severity = severity;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getPatientName() {
        return patientName;
    }

    public Location getEmergencyLocation() {
        return emergencyLocation;
    }

    public Severity getSeverity() {
        return severity;
    }

    @Override
    public int compareTo(EmergencyRequest other) {
        // Priority order: CRITICAL (ordinal 0) > SERIOUS (ordinal 1) > MINOR (ordinal 2)
        return Integer.compare(this.severity.ordinal(), other.severity.ordinal());
    }

    @Override
    public String toString() {
        return "EmergencyRequest{" +
                "requestId='" + requestId + '\'' +
                ", patientName='" + patientName + '\'' +
                ", location=" + emergencyLocation.getName() +
                ", severity=" + severity +
                '}';
    }
}