# Multi-Module Split: Core + Debug Plugins

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restructure the single Eclipse plugin into a multi-module Maven/Tycho project that produces two JARs: a core test-runner plugin and a debug plugin skeleton.

**Architecture:** Convert to a parent POM with two child modules. The core module (`eclipse.mcp.server`) contains all existing functionality. The debug module (`eclipse.mcp.server.debug`) is a separate Eclipse plugin that depends on core and registers additional tools via `ToolRegistry`. ToolRegistry becomes injectable so the debug plugin can add tools at startup.

**Tech Stack:** Maven/Tycho multi-module, Eclipse OSGi plugins, Java 17

---

## Target Directory Structure

```
eclipse-mcp-server/
├── pom.xml                              (NEW parent POM, pom packaging)
├── .github/workflows/release.yml        (UPDATED for two JARs)
├── .gitignore                           (UPDATED)
├── .mvn/                                (stays)
├── README.md / CHANGELOG.md / LICENSE   (stay)
├── eclipse.mcp.server/                  (core module, all existing code)
│   ├── pom.xml                          (child POM, eclipse-plugin packaging)
│   ├── META-INF/MANIFEST.MF             (moved, updated with Export-Package)
│   ├── plugin.xml                       (moved)
│   ├── build.properties                 (moved)
│   ├── src/main/java/eclipse/mcp/...    (moved)
│   └── src/test/java/eclipse/mcp/...    (moved)
└── eclipse.mcp.server.debug/            (NEW debug module)
    ├── pom.xml
    ├── META-INF/MANIFEST.MF
    ├── plugin.xml
    ├── build.properties
    └── src/main/java/eclipse/mcp/debug/
        └── DebugStartupHook.java
```

## Chunk 1: Restructure and Make Extensible

### Task 1: Move existing code into core submodule

**Files:**
- Move: all source and config files into `eclipse.mcp.server/` subdirectory

- [ ] **Step 1: Create the core module directory and move files**

```bash
mkdir eclipse.mcp.server
git mv META-INF eclipse.mcp.server/
git mv plugin.xml eclipse.mcp.server/
git mv build.properties eclipse.mcp.server/
git mv src eclipse.mcp.server/
```

- [ ] **Step 2: Move the existing pom.xml into the core module**

```bash
git mv pom.xml eclipse.mcp.server/pom.xml
```

- [ ] **Step 3: Commit the file moves**

```bash
git add -A
git commit -m "refactor: move existing code into eclipse.mcp.server submodule"
```

---

### Task 2: Create parent POM

**Files:**
- Create: `pom.xml` (root)
- Modify: `eclipse.mcp.server/pom.xml`

- [ ] **Step 1: Create the parent POM at root**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>eclipse.mcp</groupId>
    <artifactId>eclipse.mcp.parent</artifactId>
    <version>0.15.0-SNAPSHOT</version>
    <packaging>pom</packaging>

    <modules>
        <module>eclipse.mcp.server</module>
    </modules>

    <properties>
        <tycho.version>4.0.9</tycho.version>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <repositories>
        <repository>
            <id>eclipse-2024-12</id>
            <layout>p2</layout>
            <url>https://download.eclipse.org/releases/2024-12/</url>
        </repository>
    </repositories>

    <build>
        <plugins>
            <plugin>
                <groupId>org.eclipse.tycho</groupId>
                <artifactId>tycho-maven-plugin</artifactId>
                <version>${tycho.version}</version>
                <extensions>true</extensions>
            </plugin>

            <plugin>
                <groupId>org.eclipse.tycho</groupId>
                <artifactId>target-platform-configuration</artifactId>
                <version>${tycho.version}</version>
                <configuration>
                    <pomDependencies>consider</pomDependencies>
                    <environments>
                        <environment>
                            <os>linux</os>
                            <ws>gtk</ws>
                            <arch>x86_64</arch>
                        </environment>
                        <environment>
                            <os>macosx</os>
                            <ws>cocoa</ws>
                            <arch>x86_64</arch>
                        </environment>
                        <environment>
                            <os>macosx</os>
                            <ws>cocoa</ws>
                            <arch>aarch64</arch>
                        </environment>
                        <environment>
                            <os>win32</os>
                            <ws>win32</ws>
                            <arch>x86_64</arch>
                        </environment>
                    </environments>
                </configuration>
            </plugin>
        </plugins>

        <pluginManagement>
            <plugins>
                <plugin>
                    <groupId>org.eclipse.tycho</groupId>
                    <artifactId>tycho-compiler-plugin</artifactId>
                    <version>${tycho.version}</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                            <version>1.18.34</version>
                        </dependency>
                    </dependencies>
                </plugin>

                <plugin>
                    <groupId>org.eclipse.tycho</groupId>
                    <artifactId>tycho-surefire-plugin</artifactId>
                    <version>${tycho.version}</version>
                    <configuration>
                        <skipTests>true</skipTests>
                    </configuration>
                </plugin>
            </plugins>
        </pluginManagement>
    </build>
</project>
```

- [ ] **Step 2: Update the core module's pom.xml**

Simplify to reference the parent, keeping only module-specific config:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>eclipse.mcp</groupId>
        <artifactId>eclipse.mcp.parent</artifactId>
        <version>0.15.0-SNAPSHOT</version>
    </parent>

    <artifactId>eclipse.mcp.server</artifactId>
    <packaging>eclipse-plugin</packaging>

    <dependencies>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <version>1.18.34</version>
            <scope>provided</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.eclipse.tycho</groupId>
                <artifactId>tycho-compiler-plugin</artifactId>
                <version>${tycho.version}</version>
                <configuration>
                    <useJDK>SYSTEM</useJDK>
                    <compilerArgs>
                        <arg>--add-modules</arg>
                        <arg>jdk.httpserver</arg>
                    </compilerArgs>
                </configuration>
            </plugin>

            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.2.5</version>
                <executions>
                    <execution>
                        <id>unit-tests</id>
                        <phase>test</phase>
                        <goals>
                            <goal>test</goal>
                        </goals>
                    </execution>
                </executions>
                <dependencies>
                    <dependency>
                        <groupId>org.junit.jupiter</groupId>
                        <artifactId>junit-jupiter-engine</artifactId>
                        <version>5.11.4</version>
                    </dependency>
                </dependencies>
                <configuration>
                    <argLine>
                        -javaagent:${settings.localRepository}/net/bytebuddy/byte-buddy-agent/1.14.12/byte-buddy-agent-1.14.12.jar
                        --add-modules jdk.httpserver
                    </argLine>
                </configuration>
            </plugin>
        </plugins>
    </build>

    <profiles>
        <profile>
            <id>test-deps</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <dependencies>
                <dependency>
                    <groupId>org.junit.jupiter</groupId>
                    <artifactId>junit-jupiter</artifactId>
                    <version>5.11.4</version>
                    <scope>test</scope>
                </dependency>
                <dependency>
                    <groupId>org.mockito</groupId>
                    <artifactId>mockito-core</artifactId>
                    <version>5.11.0</version>
                    <scope>test</scope>
                </dependency>
            </dependencies>
        </profile>
    </profiles>
</project>
```

- [ ] **Step 3: Update core MANIFEST.MF version and add Export-Package**

Add `Export-Package` so the debug module can access the tools API and Activator:

```
Manifest-Version: 1.0
Bundle-ManifestVersion: 2
Bundle-Name: Eclipse Test Runner MCP
Bundle-SymbolicName: eclipse.mcp.server;singleton:=true
Bundle-Version: 0.15.0.qualifier
Bundle-Activator: eclipse.mcp.Activator
Bundle-Vendor: Eclipse MCP
Require-Bundle: org.eclipse.core.runtime,
 org.eclipse.core.resources,
 org.eclipse.debug.core,
 org.eclipse.debug.ui,
 org.eclipse.ui,
 org.eclipse.ui.console,
 org.eclipse.jface.text,
 com.google.gson,
 org.eclipse.jdt.core,
 org.eclipse.jdt.junit.core,
 org.eclipse.eclemma.core,
 org.jacoco.core
Bundle-RequiredExecutionEnvironment: JavaSE-17
Bundle-ActivationPolicy: lazy
Import-Package: com.sun.net.httpserver
Export-Package: eclipse.mcp,
 eclipse.mcp.tools
```

- [ ] **Step 4: Verify the build compiles**

```bash
cd eclipse-mcp-server && mvn verify -B
```

Expected: BUILD SUCCESS with core module built.

- [ ] **Step 5: Commit**

```bash
git add pom.xml eclipse.mcp.server/pom.xml eclipse.mcp.server/META-INF/MANIFEST.MF
git commit -m "refactor: create parent POM and configure multi-module build"
```

---

### Task 3: Make ToolRegistry extensible for external plugins

**Files:**
- Modify: `eclipse.mcp.server/src/main/java/eclipse/mcp/tools/ToolRegistry.java`
- Modify: `eclipse.mcp.server/src/main/java/eclipse/mcp/McpProtocolHandler.java`
- Modify: `eclipse.mcp.server/src/main/java/eclipse/mcp/McpHttpServer.java`
- Modify: `eclipse.mcp.server/src/main/java/eclipse/mcp/Activator.java`
- Modify: `eclipse.mcp.server/src/test/java/eclipse/mcp/McpProtocolHandlerTest.java`

The goal: ToolRegistry is created in `Activator.start()` and passed via constructor injection. Debug plugin accesses it via `Activator.getInstance().getToolRegistry()`. The `addTool()` method becomes public so external plugins can register tools.

- [ ] **Step 1: Update ToolRegistry — make addTool public**

```java
package eclipse.mcp.tools;

import com.google.gson.JsonObject;
import eclipse.mcp.tools.impl.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ToolRegistry {

    private final LinkedHashMap<String, IMcpTool> toolsByName = new LinkedHashMap<>();

    public ToolRegistry() {
        addTool(new ListProjectsTool());
        addTool(new ListLaunchConfigsTool());
        addTool(new ListLaunchesTool());
        addTool(new TerminateTool());
        addTool(new RunTestTool());
        addTool(new LaunchTestTool());
        addTool(new GetTestResultsTool());
        addTool(new GetProblemsTool());
        addTool(new GetFailureTraceTool());
        addTool(new GetConsoleOutputTool());
        addTool(new GetCoverageTool());
    }

    public synchronized void addTool(IMcpTool tool) {
        toolsByName.put(tool.getName(), tool);
    }

    public synchronized List<Map<String, Object>> listToolSchemas() {
        List<Map<String, Object>> schemas = new ArrayList<>();
        for (IMcpTool tool : toolsByName.values()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", tool.getName());
            entry.put("description", tool.getDescription());
            entry.put("inputSchema", tool.getInputSchema());
            schemas.add(entry);
        }
        return schemas;
    }

    public synchronized Object callTool(String name, JsonObject arguments) throws Exception {
        IMcpTool tool = toolsByName.get(name);
        if (tool == null) {
            throw new IllegalArgumentException("Unknown tool: " + name);
        }
        return tool.execute(new Args(arguments));
    }
}
```

Key changes: `addTool` is now `public synchronized`, `listToolSchemas` and `callTool` are `synchronized` for thread safety when debug plugin registers tools concurrently.

- [ ] **Step 2: Update McpProtocolHandler — accept ToolRegistry via constructor**

```java
// Change constructor from:
public McpProtocolHandler() {
    registry = new ToolRegistry();
}
// To:
public McpProtocolHandler(ToolRegistry registry) {
    this.registry = registry;
}
```

- [ ] **Step 3: Update McpHttpServer — accept ToolRegistry via constructor**

```java
// Change field from:
private final McpProtocolHandler protocolHandler = new McpProtocolHandler();
// To:
private final McpProtocolHandler protocolHandler;

// Add constructor:
public McpHttpServer(ToolRegistry registry) {
    this.protocolHandler = new McpProtocolHandler(registry);
}
```

- [ ] **Step 4: Update Activator — create ToolRegistry early, expose it**

```java
package eclipse.mcp;

import eclipse.mcp.tools.ToolRegistry;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

public class Activator extends AbstractUIPlugin {

    public static final String PLUGIN_ID = "eclipse.mcp.server";

    private static Activator plugin;
    private McpHttpServer server;
    private ToolRegistry toolRegistry;

    public static Activator getInstance() {
        return plugin;
    }

    public ToolRegistry getToolRegistry() {
        return toolRegistry;
    }

    @Override
    public void start(BundleContext ctx) throws Exception {
        super.start(ctx);
        plugin = this;
        toolRegistry = new ToolRegistry();
    }

    @Override
    public void stop(BundleContext ctx) throws Exception {
        shutdownServer();
        plugin = null;
        super.stop(ctx);
    }

    public void initServer() {
        if (server != null) {
            return;
        }
        server = new McpHttpServer(toolRegistry);
        try {
            server.start();
            log(IStatus.INFO, "MCP HTTP server listening on port " + McpHttpServer.PORT);
        } catch (Exception ex) {
            log(IStatus.ERROR, "MCP HTTP server failed to start: " + ex.getMessage());
            server = null;
        }
    }

    public void shutdownServer() {
        if (server == null) {
            return;
        }
        server.stop();
        server = null;
    }

    private void log(int severity, String msg) {
        getLog().log(new Status(severity, PLUGIN_ID, msg));
    }
}
```

- [ ] **Step 5: Update McpProtocolHandlerTest — pass ToolRegistry to constructor**

```java
// Change setUp from:
handler = new McpProtocolHandler();
// To:
handler = new McpProtocolHandler(new ToolRegistry());
```

- [ ] **Step 6: Run tests**

```bash
cd eclipse-mcp-server && mvn test -B -pl eclipse.mcp.server
```

Expected: All tests pass.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "refactor: make ToolRegistry injectable for external plugin registration"
```

---

## Chunk 2: Debug Module and Release Workflow

### Task 4: Create debug plugin skeleton

**Files:**
- Create: `eclipse.mcp.server.debug/pom.xml`
- Create: `eclipse.mcp.server.debug/META-INF/MANIFEST.MF`
- Create: `eclipse.mcp.server.debug/plugin.xml`
- Create: `eclipse.mcp.server.debug/build.properties`
- Create: `eclipse.mcp.server.debug/src/main/java/eclipse/mcp/debug/DebugStartupHook.java`
- Modify: `pom.xml` (parent — add module)

- [ ] **Step 1: Create debug module directory structure**

```bash
mkdir -p eclipse.mcp.server.debug/META-INF
mkdir -p eclipse.mcp.server.debug/src/main/java/eclipse/mcp/debug
```

- [ ] **Step 2: Create debug pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>eclipse.mcp</groupId>
        <artifactId>eclipse.mcp.parent</artifactId>
        <version>0.15.0-SNAPSHOT</version>
    </parent>

    <artifactId>eclipse.mcp.server.debug</artifactId>
    <packaging>eclipse-plugin</packaging>
</project>
```

- [ ] **Step 3: Create debug MANIFEST.MF**

```
Manifest-Version: 1.0
Bundle-ManifestVersion: 2
Bundle-Name: Eclipse MCP Debug Tools
Bundle-SymbolicName: eclipse.mcp.server.debug;singleton:=true
Bundle-Version: 0.15.0.qualifier
Bundle-Vendor: Eclipse MCP
Require-Bundle: org.eclipse.core.runtime,
 org.eclipse.ui,
 org.eclipse.debug.core,
 eclipse.mcp.server
Bundle-RequiredExecutionEnvironment: JavaSE-17
Bundle-ActivationPolicy: lazy
```

- [ ] **Step 4: Create debug plugin.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<plugin>
   <extension point="org.eclipse.ui.startup">
      <startup class="eclipse.mcp.debug.DebugStartupHook"/>
   </extension>
</plugin>
```

- [ ] **Step 5: Create debug build.properties**

```
source.. = src/main/java/
output.. = target/classes/
bin.includes = META-INF/,\
               plugin.xml,\
               .
```

- [ ] **Step 6: Create DebugStartupHook.java**

```java
package eclipse.mcp.debug;

import eclipse.mcp.Activator;
import eclipse.mcp.tools.ToolRegistry;
import org.eclipse.ui.IStartup;

public class DebugStartupHook implements IStartup {

    @Override
    public void earlyStartup() {
        ToolRegistry registry = Activator.getInstance().getToolRegistry();
        if (registry == null) {
            return;
        }
        // Debug tools will be registered here as they are implemented.
        // Example:
        // registry.addTool(new SetBreakpointTool());
        // registry.addTool(new ListBreakpointsTool());
        // registry.addTool(new LaunchDebugTool());
        // registry.addTool(new ListThreadsTool());
        // registry.addTool(new GetStackTraceTool());
    }
}
```

- [ ] **Step 7: Add debug module to parent POM**

In `pom.xml` (root), update modules:

```xml
<modules>
    <module>eclipse.mcp.server</module>
    <module>eclipse.mcp.server.debug</module>
</modules>
```

- [ ] **Step 8: Verify full build**

```bash
cd eclipse-mcp-server && mvn verify -B
```

Expected: Both modules build successfully, core tests pass.

- [ ] **Step 9: Commit**

```bash
git add -A
git commit -m "feat: add eclipse.mcp.server.debug plugin skeleton"
```

---

### Task 5: Update release workflow for two JARs

**Files:**
- Modify: `.github/workflows/release.yml`

- [ ] **Step 1: Update release.yml**

Key changes:
- Extract version from parent POM
- Build from root (builds both modules)
- Copy both JARs to release assets
- Name JARs clearly: core vs debug

```yaml
name: Release

on:
  push:
    branches: [main]

jobs:
  publish:
    runs-on: ubuntu-latest
    permissions:
      contents: write

    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 17
          cache: maven

      - name: Extract version
        id: meta
        run: |
          RAW=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout -N)
          VERSION="${RAW%-SNAPSHOT}"
          echo "tag=v${VERSION}" >> "$GITHUB_OUTPUT"
          echo "version=${VERSION}" >> "$GITHUB_OUTPUT"
          echo "core_jar=eclipse.mcp.server_${VERSION}.jar" >> "$GITHUB_OUTPUT"
          echo "debug_jar=eclipse.mcp.server.debug_${VERSION}.jar" >> "$GITHUB_OUTPUT"

      - name: Skip if already released
        id: guard
        run: |
          gh release view "${{ steps.meta.outputs.tag }}" &>/dev/null \
            && echo "skip=true" >> "$GITHUB_OUTPUT" \
            || echo "skip=false" >> "$GITHUB_OUTPUT"
        env:
          GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}

      - name: Build
        if: steps.guard.outputs.skip == 'false'
        run: mvn verify -B

      - name: Publish release
        if: steps.guard.outputs.skip == 'false'
        run: |
          cp eclipse.mcp.server/target/eclipse.mcp.server-*.jar \
             "${{ steps.meta.outputs.core_jar }}"
          cp eclipse.mcp.server.debug/target/eclipse.mcp.server.debug-*.jar \
             "${{ steps.meta.outputs.debug_jar }}"

          PREV_TAG=$(gh release list --limit 1 --json tagName -q '.[0].tagName' 2>/dev/null || true)
          if [ -n "$PREV_TAG" ]; then
            RANGE="${PREV_TAG}..HEAD"
          else
            RANGE="HEAD"
          fi

          BODY="## Changes"$'\n\n'
          BODY+="| Commit | Message |"$'\n'
          BODY+="|--------|---------|"$'\n'
          REPO_URL="${{ github.server_url }}/${{ github.repository }}"
          BODY+=$(git log "$RANGE" --pretty=format:"| [\`%h\`](${REPO_URL}/commit/%H) | %s |")

          if [ -n "$PREV_TAG" ]; then
            BODY+=$'\n\n'"**[Full diff](${REPO_URL}/compare/${PREV_TAG}...${{ steps.meta.outputs.tag }})**"
          fi

          BODY+=$'\n\n'"## Installation"$'\n\n'
          BODY+="| JAR | Description |"$'\n'
          BODY+="|-----|-------------|"$'\n'
          BODY+="| \`${{ steps.meta.outputs.core_jar }}\` | Core test runner (required) |"$'\n'
          BODY+="| \`${{ steps.meta.outputs.debug_jar }}\` | Debug tools add-on (optional, requires core) |"

          gh release create "${{ steps.meta.outputs.tag }}" \
            "${{ steps.meta.outputs.core_jar }}" \
            "${{ steps.meta.outputs.debug_jar }}" \
            --title "${{ steps.meta.outputs.tag }}" \
            --notes "$BODY"
        env:
          GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```

- [ ] **Step 2: Commit**

```bash
git add .github/workflows/release.yml
git commit -m "ci: update release workflow to publish core and debug JARs"
```

---

### Task 6: Update .gitignore and docs

**Files:**
- Modify: `.gitignore`
- Modify: `README.md` (installation section)
- Modify: `CHANGELOG.md`

- [ ] **Step 1: Verify .gitignore**

The existing `target/` pattern (without leading `/`) already matches `eclipse.mcp.server/target/` and `eclipse.mcp.server.debug/target/` at any depth. No changes needed.

- [ ] **Step 2: Update README.md installation section**

Update to mention both JARs and that debug is optional.

- [ ] **Step 3: Update CHANGELOG.md**

```markdown
## v0.15.0

- Restructure into multi-module Maven project (core + debug)
  - `eclipse.mcp.server` — core test runner plugin (all existing tools)
  - `eclipse.mcp.server.debug` — debug tools add-on (skeleton, requires core)
- Each release now produces two JARs
- ToolRegistry is now extensible — external plugins can register additional tools
```

- [ ] **Step 4: Commit**

```bash
git add .gitignore README.md CHANGELOG.md
git commit -m "docs: update for multi-module structure and v0.15.0"
```
