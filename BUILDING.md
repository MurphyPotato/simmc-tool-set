# Building simMC Tool Set

The project can be compiled without the optional Xaero libraries. In that mode
the non-map modules are built and the SIMMC map implementation is left out.

To enable the map implementation, place exactly these two files in `libs/`:

- `XaerosWorldMap_1.39.13_Fabric_1.21.8.jar`
- `Xaeros_Minimap_25.2.16_Fabric_1.21.8.jar`

The two jars remain external runtime dependencies and are never packaged into
the Tool Set jar. The Gradle task `checkXaeroBuildConfiguration` reports which
mode is selected. Run it with the repository's Gradle wrapper (when present),
for example `./gradlew checkXaeroBuildConfiguration`. An alternate directory
can be supplied without copying files by passing
`-Pxaero_lib_dir=C:/path/to/xaero-libs`.
