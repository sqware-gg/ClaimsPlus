package dev.claimsplus.claim;

public enum ClaimDirection {
    NORTH("north"),
    EAST("east"),
    SOUTH("south"),
    WEST("west");

    private final String label;

    ClaimDirection(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static ClaimDirection fromYaw(float yaw) {
        float normalized = yaw % 360.0F;
        if (normalized < 0.0F) {
            normalized += 360.0F;
        }
        if (normalized < 45.0F || normalized >= 315.0F) {
            return SOUTH;
        }
        if (normalized < 135.0F) {
            return WEST;
        }
        if (normalized < 225.0F) {
            return NORTH;
        }
        return EAST;
    }
}
