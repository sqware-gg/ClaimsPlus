# ClaimsPlus Comparison Notes

## GriefPrevention

GriefPrevention is player-facing and command-driven. Its useful pattern for this plugin is tiered trust:

- Access trust: doors, buttons, levers, beds, and similar access blocks.
- Container trust: access trust plus containers, crafting gear, animals, and vehicles.
- Build trust: edit/build access.
- Public trust: grant access/container/build to everyone.

ClaimsPlus now follows that model with `/accesstrust`, `/containertrust`, `/trust`, `/permissiontrust`, `/untrust`, and public trust support.

## GriefDefender

GriefDefender is policy-driven. Its useful pattern for this plugin is evaluating actions with source/target context instead of only checking block break/place.

ClaimsPlus does not implement a full flag engine, but it now applies the same idea to core Minecraft events:

- Player action source: build, access, container, and entity actions require the matching trust tier.
- Non-player source: explosions, pistons, fluids, hoppers, portals, fertilizer, sponge absorption, and structure growth cannot cross into protected claims.
- Target context: passive entities, villagers, armor stands, hanging entities, vehicles, containers, drops, and claim anchors have separate checks.

## Deliberate Scope

ClaimsPlus remains an emerald-block claim plugin:

- Claims are fixed 32x32 columns by default.
- No subdivisions, economy, claim-block accrual, GUI flag editor, or LuckPerms-backed flags.
- The config exposes broad protection categories instead of hundreds of flag definitions.

That keeps it predictable while covering the high-risk bypass paths expected from mature land protection plugins.
