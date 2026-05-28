package dev.claimsplus.claim;

import java.util.EnumSet;
import java.util.Set;

public final class TrustEntry {
    private String name;
    private final EnumSet<TrustType> types;

    public TrustEntry(String name, Set<TrustType> types) {
        this.name = cleanName(name);
        this.types = types.isEmpty() ? EnumSet.noneOf(TrustType.class) : EnumSet.copyOf(types);
    }

    public String name() {
        return name;
    }

    public void updateName(String name) {
        this.name = cleanName(name);
    }

    public boolean grant(TrustType type) {
        return types.add(type);
    }

    public boolean has(TrustType type) {
        return switch (type) {
            case BUILD -> types.contains(TrustType.BUILD);
            case CONTAINER -> types.contains(TrustType.BUILD) || types.contains(TrustType.CONTAINER);
            case ACCESS -> types.contains(TrustType.BUILD)
                    || types.contains(TrustType.CONTAINER)
                    || types.contains(TrustType.ACCESS);
            case PERMISSION -> types.contains(TrustType.PERMISSION);
        };
    }

    public Set<TrustType> types() {
        return EnumSet.copyOf(types);
    }

    public boolean empty() {
        return types.isEmpty();
    }

    private String cleanName(String name) {
        return name == null || name.isBlank() ? "Unknown" : name;
    }
}
