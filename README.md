# ClaimsPlus

**Join the SQWARE Discord: [discord.sqware.gg](https://discord.sqware.gg).**

ClaimsPlus is a GriefPrevention-style land claim plugin for Paper servers. Players place an emerald block to create a protected 32x32 claim, then manage trust with simple commands.

Use it when you want Minecraft land protection that is easier to explain than region tools and lighter than complex claim systems.

## Features

- Emerald block claim anchors.
- 32x32 claims from world bottom to world top.
- Owner-only anchor breaking.
- Build, access, container, and permission trust levels.
- Public trust support for shared farms, doors, or containers.
- Temporary client-side claim border preview.
- Protection for common bypasses: explosions, pistons, fluid flow, hoppers, containers, dispensers, sponge absorption, tree growth, item frames, armor stands, vehicles, mobs, villagers, shearing, leashing, and buckets.
- Claim data saved in `plugins/ClaimsPlus/claims.yml`.

## Requirements

- Paper `26.1.2+`
- Java `25+`
- Maven wrapper included

## Claiming

Place an emerald block to create a claim. The emerald block is the anchor. Breaking the anchor removes the claim, but only the owner or a player with `claimsplus.bypass` can break it.

When a claim is created, the owner briefly sees a fake block border. No real border blocks are placed.

## Commands

```text
/trust <player>
/accesstrust <player>
/containertrust <player>
/permissiontrust <player>
/untrust <player>
/trustlist
/claiminfo
/claimshow
/claims
/claims info
/claims show
/claims status
/claims deletehere
/claims delete <id>
/claims reload
/claims save
```

Use `public` instead of a player name with `/trust`, `/accesstrust`, or `/containertrust` to grant that tier to everyone.

Aliases: `/at`, `/ct`, `/pt`, `/claimborder`, `/showclaim`, `/claim`

## Permissions

```text
claimsplus.create  - create claims
claimsplus.trust   - manage trust in owned claims
claimsplus.info    - view claim info and own claims
claimsplus.admin   - admin commands
claimsplus.reload  - reload config
claimsplus.save    - save data
claimsplus.delete  - delete claims
claimsplus.bypass  - bypass protection and anchor ownership
```

## Build

```powershell
.\mvnw.cmd package
```

The jar is written to `target/ClaimsPlus-0.1.0.jar`.

## License

ClaimsPlus is licensed under the Apache License, Version 2.0.
