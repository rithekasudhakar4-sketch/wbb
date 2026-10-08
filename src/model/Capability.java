package model;

/**
 * Specialized medical facilities and units available across hospitals.
 */
public enum Capability {
    BURN_UNIT("Burn & Plastic Care Unit"),
    CARDIAC_CARE("Advanced Cardiac Care & Cath Lab"),
    TRAUMA_CENTRE("Level-1 Emergency Trauma Care"),
    PEDIATRIC_ICU("Pediatric & Neonatal ICU"),
    STROKE_UNIT("Comprehensive Stroke & Neurology Unit"),
    ICU("Intensive Care Unit (Critical Support)"),
    GENERAL_WARD("General Inpatient Care");

    private final String displayName;

    Capability(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
