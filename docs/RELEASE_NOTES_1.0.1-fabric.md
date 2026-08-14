# simMC Tool Set 1.0.1-fabric

## Fixed

- Narrowed the Mixin package boundary so the client entrypoint is no longer treated as a Mixin class during Fabric startup.
- Kept optional Xaero map mixins isolated from the non-map build output.

## Verification

- Minecraft 1.21.8
- Java 21
- Fabric Loader 0.17.3
- Fabric API 0.136.1+1.21.8
- `clean test build` completed successfully with Xaero World Map 1.39.13 and Xaero Minimap 25.2.16 compile inputs.
