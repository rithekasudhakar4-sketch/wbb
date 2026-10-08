package model;

/**
 * Emergency patient conditions and their primary required medical facility.
 */
public enum Condition {
    BURN("Severe Burn Injury", Capability.BURN_UNIT, Severity.CRITICAL),
    CARDIAC_ARREST("Cardiac Arrest / Acute Coronary", Capability.CARDIAC_CARE, Severity.CRITICAL),
    TRAUMA("Severe Multi-Trauma / Accident", Capability.TRAUMA_CENTRE, Severity.CRITICAL),
    STROKE("Acute Ischemic/Hemorrhagic Stroke", Capability.STROKE_UNIT, Severity.CRITICAL),
    PEDIATRIC_EMERGENCY("Pediatric Respiratory/Crisis", Capability.PEDIATRIC_ICU, Severity.SERIOUS),
    RESPIRATORY_FAILURE("Severe Respiratory Distress", Capability.ICU, Severity.SERIOUS),
    FRACTURE("Bone Fracture / Minor Trauma", Capability.GENERAL_WARD, Severity.SERIOUS),
    GENERAL_ILLNESS("Acute Fever / Dehydration / Mild Pain", Capability.GENERAL_WARD, Severity.MINOR);

    private final String displayName;
    private final Capability defaultRequiredCapability;
    private final Severity defaultSeverity;

    Condition(String displayName, Capability defaultRequiredCapability, Severity defaultSeverity) {
        this.displayName = displayName;
        this.defaultRequiredCapability = defaultRequiredCapability;
        this.defaultSeverity = defaultSeverity;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Capability getDefaultRequiredCapability() {
        return defaultRequiredCapability;
    }

    public Severity getDefaultSeverity() {
        return defaultSeverity;
    }
}
