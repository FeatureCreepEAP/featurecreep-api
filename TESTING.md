# FeatureCreep API v12 tests

FeatureCreep API now requires **JDK 25 or newer**.

Run the complete suite with:

```bash
mvn clean test
```

The attach tests include:

- `AttachProtocolTest` — HotSpot attach protocol parsing and input validation.
- `PanamaNativeTest` — verifies POSIX native calls are reached through `java.lang.foreign` rather than JNA.
- `AgentAttachIntegrationTest` — creates a temporary Java agent JAR, starts a separate plain JVM, attaches through `featurecreep.attach.Attach`, and verifies `agentmain` receives the requested argument and a real `Instrumentation` instance.

## Important property of the integration test

The target JVM is intentionally started with only a class path and the test main class. It does **not** receive:

- `-Djdk.attach.allowAttachSelf=true`
- `-XX:+StartAttachListener`
- `-XX:+EnableDynamicAgentLoading`
- `--enable-native-access=...`

The target is reached through FeatureCreep's direct HotSpot attach protocol; the implementation does not call `jdk.attach.VirtualMachine`.

The Maven Surefire JVM (the process doing the attach) is started with
`--enable-native-access=ALL-UNNAMED` because FeatureCreep API now performs native calls through Java 25's Foreign Function & Memory API. That setting is unrelated to enabling the target JVM's attach mechanism.

The integration test is inherently platform-sensitive. It currently exercises the active FeatureCreep provider for Linux, macOS/BSD, Solaris, AIX, or Windows. A platform/JVM configured with `-XX:+DisableAttachMechanism`, OS sandboxing that blocks signaling/process access, or a security product that blocks remote-thread injection can correctly cause the integration test to fail.
