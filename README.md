# ClaimsPlus

ClaimsPlus protects land with emerald blocks.

## Claiming

Place an emerald block to create a 32x32 claim around that block. The claim protects the whole vertical column from the bottom of the world to the top.

The emerald block is the claim anchor. Breaking the anchor removes the claim, but only the owner or a player with `claimsplus.bypass` can break it.

When a claim is created, ClaimsPlus briefly shows the owner a client-side fake block border. It defaults to glowstone and can be changed under `visual-border` in `config.yml`. No real blocks are placed.

Sounds are configurable under `feedback.sounds`. Protection denial feedback is throttled by `feedback.protected-cooldown-millis` so repeated blocked clicks do not flood chat or audio.

## Commands

- `/trust <player>` - give build trust. This includes block editing, container trust, and access trust.
- `/accesstrust <player>` - let a player use doors, buttons, levers, beds, and similar access blocks.
- `/containertrust <player>` - let a player use inventories, animals, villagers, vehicles, and item drops.
- `/permissiontrust <player>` - let a player manage trust in the claim.
- `/untrust <player>` - remove trust from the claim you are standing in.

Use `public` instead of a player name with `/trust`, `/accesstrust`, or `/containertrust` to grant that tier to everyone.

Online players are notified in chat when another player grants or removes their trust.
- `/trustlist` - list trusted players in the claim you are standing in.
- `/claims` - list your claims.
- `/claims info` - view the claim at your location.
- `/claims show` - temporarily show the border for the claim you are standing in.
- `/claims status|deletehere|delete <id>|reload|save` - admin controls.

## Protection Coverage

ClaimsPlus blocks normal building and common bypasses, including explosions, pistons, fluid flow across claim borders, hoppers across claim borders, container access, dispenser placement, sponge absorption, tree growth across borders, item frames, armor stands, vehicles, passive mobs, villagers, shearing, leashing, and bucket use.

Configuration lives in `plugins/ClaimsPlus/config.yml`; claim data is saved in `plugins/ClaimsPlus/claims.yml`.
