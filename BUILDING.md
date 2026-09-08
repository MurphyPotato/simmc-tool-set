# Building simMC Tool Set

Use Java 21 and the repository Gradle wrapper:

```text
./gradlew --no-daemon clean verifyUnitTests build -x test
```

The web-map module has been removed. No map libraries are needed to compile or
run the Tool Set. The optional Mod Menu integration is compiled when one
compatible `modmenu-*.jar` is present in `libs/`.
