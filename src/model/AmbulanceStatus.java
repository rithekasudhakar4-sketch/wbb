package model;

public enum AmbulanceStatus {
    AVAILABLE("Available"),
    EN_ROUTE_PATIENT("En Route to Patient"),
    EN_ROUTE_HOSPITAL("En Route to Hospital"),
    BUSY("Busy on Call");

    private final String description;

    AmbulanceStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
