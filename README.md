# FeatureCreep API 12

FeatureCreep API 12 targets **Java 25+**.

## Direct agent attach

The API retains FeatureCreep's direct HotSpot attach implementation. It does not use `jdk.attach.VirtualMachine` and therefore does not inherit the Attach API's self-attach policy. The implementation speaks HotSpot's native attach protocol directly:

- Linux: `.attach_pid` / SIGQUIT / Unix-domain attach socket
- macOS and BSD: `.attach_pid` / SIGQUIT / Unix-domain attach socket
- AIX: `.attach_pid` / SIGQUIT / Unix-domain attach socket
- Solaris: `.attach_pid` / SIGQUIT / Solaris doors
- Windows: `JVM_EnqueueOperation` remote-thread/named-pipe path

JNA and `jna-platform` have been removed. Native OS calls are implemented with the finalized Java 25 Foreign Function & Memory API (`java.lang.foreign`, Project Panama). Where the Java runtime already has a suitable first-class API, such as Unix-domain `SocketChannel`, FeatureCreep uses that instead of manually recreating C socket structures.

Typical use remains:

```java
Attach.attach("/absolute/path/to/agent.jar", "agent arguments");
```

An explicit target PID is also supported:

```java
Attach.attach(pid, "/absolute/path/to/agent.jar", "agent arguments");
```

## Native access

Project Panama's linker/downcall entry points are restricted Java APIs. Applications using the direct native attach path should grant native access to the module containing FeatureCreep API (or `ALL-UNNAMED` when used from the class path). This is a native-access permission for the **attaching JVM**, not an Attach API flag for the target JVM.

## Build

```bash
mvn clean test
mvn clean install
```

Central signing remains deployment-only:

```bash
mvn clean deploy -Pcentral-release
```
