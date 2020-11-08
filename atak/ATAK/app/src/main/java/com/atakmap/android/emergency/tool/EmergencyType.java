
package com.atakmap.android.emergency.tool;

public enum EmergencyType {
    NineOneOne("911 Alert", "b-a-o-tbl", 0),
    Cancel("Cancel Alert", "b-a-o-can", 1),
    GeoFenceBreach("Geo-fence Breached", "b-a-g", 2),
    RingTheBell("Ring The Bell", "b-a-o-pan", 3),
    TroopsInContact("In Contact", "b-a-o-opn", 4);

    private final String description;
    private final String cotType;
    private final int code;

    EmergencyType(String description, String cotType, int code) {
        this.description = description;
        this.cotType = cotType;
        this.code = code;
    }

    public static EmergencyType fromDescription(String description) {
        for (EmergencyType type : EmergencyType.values()) {
            if (type.getDescription().equals(description)) {
                return type;
            }
        }

        return NineOneOne;
    }

    public String getDescription() {
        return description;
    }

    public String getCoTType() {
        return cotType;
    }

    public int getCode() {
        return code;
    }

    public static EmergencyType getDefault() {
        return NineOneOne;
    }
}
