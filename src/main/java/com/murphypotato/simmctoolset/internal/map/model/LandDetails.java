package com.murphypotato.simmctoolset.internal.map.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/** Immutable, sanitized projection of the public Lands popup fields. */
public record LandDetails(
        Optional<String> landName,
        Optional<String> description,
        Optional<String> level,
        Optional<String> owner,
        Optional<String> balanceText,
        OptionalInt claimedChunks,
        OptionalInt playerCount,
        List<String> members,
        Optional<String> nationName,
        Optional<String> nationLevel,
        Optional<String> nationCapital,
        OptionalInt nationTerritoryCount,
        OptionalInt nationPlayerCount,
        List<String> nationTerritories,
        String rawText,
        List<String> unrecognizedLines
) {
    public LandDetails {
        landName = requireOptional(landName, "landName");
        description = requireOptional(description, "description");
        level = requireOptional(level, "level");
        owner = requireOptional(owner, "owner");
        balanceText = requireOptional(balanceText, "balanceText");
        Objects.requireNonNull(claimedChunks, "claimedChunks");
        Objects.requireNonNull(playerCount, "playerCount");
        members = List.copyOf(members);
        nationName = requireOptional(nationName, "nationName");
        nationLevel = requireOptional(nationLevel, "nationLevel");
        nationCapital = requireOptional(nationCapital, "nationCapital");
        Objects.requireNonNull(nationTerritoryCount, "nationTerritoryCount");
        Objects.requireNonNull(nationPlayerCount, "nationPlayerCount");
        nationTerritories = List.copyOf(nationTerritories);
        rawText = Objects.requireNonNull(rawText, "rawText");
        unrecognizedLines = List.copyOf(unrecognizedLines);
    }

    private static Optional<String> requireOptional(Optional<String> value, String name) {
        Objects.requireNonNull(value, name);
        return value.map(String::trim).filter(text -> !text.isEmpty());
    }
}
