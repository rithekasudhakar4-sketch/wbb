package model;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Represents an emergency call intake and patient dispatch record.
 * Implements Comparable for PriorityQueue ordering by Severity, breaking ties by call timestamp.
 */
public class EmergencyRequest implements Comparable<EmergencyRequest> {
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final String requestId;
    private final String patientName;
    private final Location emergencyLocation;
    private final Severity severity;
    private final Condition condition;
    private final Capability requiredCapability;
    private final long timestamp;

    private Ambulance assignedAmbulance;
    private Hospital assignedHospital;
    private String status; // PENDING, DISPATCHED, COMPLETED

    public EmergencyRequest(String requestId, String patientName, Location emergencyLocation,
                            Severity severity, Condition condition, Capability requiredCapability) {
        this.requestId = requestId;
        this.patientName = patientName;
        this.emergencyLocation = emergencyLocation;
        this.severity = severity;
        this.condition = condition;
        this.requiredCapability = requiredCapability;
        this.timestamp = System.currentTimeMillis();
        this.status = "PENDING";
    }

    public EmergencyRequest(String requestId, String patientName, Location emergencyLocation, Severity severity) {
        this(requestId, patientName, emergencyLocation, severity, Condition.GENERAL_ILLNESS, Capability.GENERAL_WARD);
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

    public Condition getCondition() {
        return condition;
    }

    public Capability getRequiredCapability() {
        return requiredCapability;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getFormattedTime() {
        return TIME_FORMATTER.format(Instant.ofEpochMilli(timestamp));
    }

    public Ambulance getAssignedAmbulance() {
        return assignedAmbulance;
    }

    public void setAssignedAmbulance(Ambulance assignedAmbulance) {
        this.assignedAmbulance = assignedAmbulance;
    }

    public Hospital getAssignedHospital() {
        return assignedHospital;
    }

    public void setAssignedHospital(Hospital assignedHospital) {
        this.assignedHospital = assignedHospital;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public int compareTo(EmergencyRequest other) {
        // 1. Primary order: CRITICAL (0) < SERIOUS (1) < MINOR (2)
        int severityComparison = Integer.compare(this.severity.ordinal(), other.severity.ordinal());
        if (severityComparison != 0) {
            return severityComparison;
        }
        // 2. Secondary order (FIFO for same severity level): earlier timestamp comes first
        return Long.compare(this.timestamp, other.timestamp);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmergencyRequest that = (EmergencyRequest) o;
        return Objects.equals(requestId, that.requestId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(requestId);
    }

    @Override
    public String toString() {
        return String.format("[%s] %s | Condition: %s | Zone: %s | Severity: %s | Required Facility: %s",
                requestId, patientName,
                condition != null ? condition.name() : "N/A",
                emergencyLocation.getName(),
                severity,
                requiredCapability != null ? requiredCapability.name() : "N/A");
    }
}