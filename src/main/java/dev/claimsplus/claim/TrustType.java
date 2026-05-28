package dev.claimsplus.claim;

public enum TrustType {
    ACCESS("access", "doors, buttons, levers, beds"),
    CONTAINER("container", "containers, animals, villagers, vehicles"),
    BUILD("build", "placing and breaking blocks"),
    PERMISSION("permission", "managing trust");

    private final String label;
    private final String description;

    TrustType(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }
}
