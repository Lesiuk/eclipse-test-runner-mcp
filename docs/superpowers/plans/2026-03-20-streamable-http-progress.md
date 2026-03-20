# Streamable HTTP with Progress Notifications — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade the MCP transport to support SSE responses with progress notifications so long-running tools (run_test, clean_build) don't timeout.

**Architecture:** When a `tools/call` request includes `_meta.progressToken` and the client accepts `text/event-stream`, the server responds with an SSE stream. Progress events (`notifications/progress`) are sent during execution, resetting the client's 60s timeout. The final JSON-RPC response is the last SSE event.

**Tech Stack:** Java 17, com.sun.net.httpserver, Gson, JUnit 5, Mockito 5

**Spec:** `docs/superpowers/specs/2026-03-20-streamable-http-progress-design.md`

**Build command:** `cd eclipse.mcp.server && mvn test -q`

**Source root:** `eclipse.mcp.server/src/main/java/uk/l3si/eclipse/mcp/`

**Test root:** `eclipse.mcp.server/src/test/java/uk/l3si/eclipse/mcp/`

---

### Task 1: ProgressReporter interface and McpTool default method

**Files:**
- Create: `src/main/java/uk/l3si/eclipse/mcp/tools/ProgressReporter.java`
- Modify: `src/main/java/uk/l3si/eclipse/mcp/tools/McpTool.java`
- Modify: `src/test/java/uk/l3si/eclipse/mcp/tools/ToolRegistryTest.java`

- [ ] **Step 1: Create ProgressReporter interface**

```java
package uk.l3si.eclipse.mcp.tools;

/**
 * Callback for reporting progress during long-running tool execution.
 * Each call resets the MCP client's timeout via notifications/progress.
 */
@FunctionalInterface
public interface ProgressReporter {
    void report(String message);
}
```

- [ ] **Step 2: Replace execute signature in McpTool**

In `McpTool.java`, replace the existing `execute(Args)` with:

```java
Object execute(Args args, ProgressReporter progress) throws Exception;
```

Remove the old `execute(Args)` — there is no default method, no backward compat. All tool implementations must accept `ProgressReporter`.

- [ ] **Step 3: Update all McpTool implementations**

Every tool that implements `McpTool` must update its `execute` signature from `execute(Args args)` to `execute(Args args, ProgressReporter progress)`. Tools that don't use progress simply ignore the parameter. This is a bulk change across all tool classes:

Core: `ListProjectsTool`, `ListLaunchConfigsTool`, `ListLaunchesTool`, `TerminateTool`, `RunTestTool`, `GetTestResultsTool`, `GetProblemsTool`, `GetConsoleOutputTool`, `GetCoverageTool`, `FindReferencesTool`, `CleanBuildTool`

Debugging: `BreakpointTool`, `GetDebugStateTool`, `GetStackTraceTool`, `ListVariablesTool`, `EvaluateExpressionTool`, `StepTool`

BPMN2: all 14 tools

Also update `ToolRegistryTest.dummyTool` — its anonymous `McpTool` implementation must match the new signature.

- [ ] **Step 4: Verify all tests still pass**

Run: `cd eclipse.mcp.server && mvn test -q`
Expected: All tests pass — tools now accept `ProgressReporter` but don't use it yet.

- [ ] **Step 4: Commit**

```
feat: add ProgressReporter interface and McpTool default method
```

---

### Task 2: ToolRegistry callTool with ProgressReporter

**Files:**
- Modify: `src/main/java/uk/l3si/eclipse/mcp/tools/ToolRegistry.java:112-122`
- Modify: `src/test/java/uk/l3si/eclipse/mcp/tools/ToolRegistryTest.java`

- [ ] **Step 1: Write test for callTool with ProgressReporter**

In `ToolRegistryTest.java`, add:

```java
@Test
void callToolPassesProgressReporterThrough() throws Exception {
    var messages = new java.util.ArrayList<String>();
    McpTool tool = new McpTool() {
        @Override public String getName() { return "progress_tool"; }
        @Override public String getDescription() { return "test"; }
        @Override public InputSchema getInputSchema() { return InputSchema.builder().build(); }
        @Override public Object execute(Args args, ProgressReporter progress) {
            progress.report("hello");
            return "with-progress";
        }
    };
    ToolRegistry registry = new ToolRegistry();
    registry.addTool(tool);

    Object result = registry.callTool("progress_tool", new JsonObject(), messages::add);
    assertEquals("with-progress", result);
    assertEquals(List.of("hello"), messages);
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd eclipse.mcp.server && mvn test -q -pl . -Dtest=ToolRegistryTest`
Expected: FAIL — `callTool` doesn't accept `ProgressReporter` yet.

- [ ] **Step 3: Update callTool signature in ToolRegistry**

Replace the existing `callTool` with:

```java
public Object callTool(String name, JsonObject arguments, ProgressReporter progress) throws Exception {
    McpTool tool;
    synchronized (this) {
        tool = toolsByName.get(name);
        if (tool == null || disabledTools.contains(name)) {
            throw new IllegalArgumentException("Unknown tool: " + name);
        }
        validateParameters(name, tool, arguments);
    }
    return tool.execute(new Args(arguments), progress);
}
```

Update all callers of the old `callTool(name, args)` — this includes `McpProtocolHandler.executeTool` and existing tests. Pass `message -> {}` where no progress is needed.

- [ ] **Step 4: Run tests**

Run: `cd eclipse.mcp.server && mvn test -q`
Expected: All pass.

- [ ] **Step 5: Commit**

```
feat: update ToolRegistry.callTool to require ProgressReporter
```

---

### Task 3: SSE writing in McpProtocolHandler

**Files:**
- Modify: `src/main/java/uk/l3si/eclipse/mcp/McpProtocolHandler.java`
- Modify: `src/test/java/uk/l3si/eclipse/mcp/McpProtocolHandlerTest.java`

- [ ] **Step 1: Update existing test and write new tests**

In `McpProtocolHandlerTest.java`:

First, update the existing `initializeReturnsProtocolInfo` test — change the expected protocol version from `"2024-11-05"` to `"2025-03-26"` (line 40).

Then add new tests:

```java
@Test
void toolCallWithProgressTokenWritesSSE() throws Exception {
    String json = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\","
            + "\"params\":{\"name\":\"list_projects\",\"_meta\":{\"progressToken\":\"tok1\"}}}";

    var out = new java.io.ByteArrayOutputStream();
    handler.handleMessage(json, out);
    String sse = out.toString(java.nio.charset.StandardCharsets.UTF_8);

    // Should contain at least the final response as an SSE event
    assertTrue(sse.contains("event: message"), "should have SSE event type");
    assertTrue(sse.contains("\"id\":1"), "should contain the response id");
    assertTrue(sse.contains("data: "), "should be SSE formatted");
}

@Test
void toolCallWithProgressTokenAndErrorWritesSSEError() throws Exception {
    String json = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\","
            + "\"params\":{\"name\":\"nonexistent\",\"_meta\":{\"progressToken\":\"tok2\"}}}";

    var out = new java.io.ByteArrayOutputStream();
    handler.handleMessage(json, out);
    String sse = out.toString(java.nio.charset.StandardCharsets.UTF_8);

    assertTrue(sse.contains("event: message"));
    assertTrue(sse.contains("isError"));
}

@Test
void toolCallWithoutProgressTokenReturnsJson() {
    String json = "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\","
            + "\"params\":{\"name\":\"list_projects\"}}";
    // Should still work via the old String-returning method
    String response = handler.handleMessage(json);
    assertNotNull(response);
}

@Test
void nonToolsCallViaSseWritesResponseAsEvent() throws Exception {
    String json = "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"ping\"}";
    var out = new java.io.ByteArrayOutputStream();
    handler.handleMessage(json, out);
    String sse = out.toString(java.nio.charset.StandardCharsets.UTF_8);
    assertTrue(sse.contains("event: message"));
    assertTrue(sse.contains("\"id\":4"));
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd eclipse.mcp.server && mvn test -q -pl . -Dtest=McpProtocolHandlerTest`
Expected: FAIL — `handleMessage(String, OutputStream)` doesn't exist; protocol version is still `2024-11-05`.

- [ ] **Step 3: Implement SSE support in McpProtocolHandler**

Changes to `McpProtocolHandler.java`:

1. Update `MCP_VERSION` to `"2025-03-26"`.

2. Add helper method to extract progress token:
```java
private String extractProgressToken(JsonObject params) {
    if (params.has("_meta")) {
        JsonObject meta = params.getAsJsonObject("_meta");
        if (meta.has("progressToken")) {
            return meta.get("progressToken").getAsString();
        }
    }
    return null;
}
```

3. Add SSE writing helpers:
```java
private void writeSseEvent(OutputStream out, int id, String json) throws IOException {
    String event = "id: " + id + "\nevent: message\ndata: " + json + "\n\n";
    out.write(event.getBytes(StandardCharsets.UTF_8));
    out.flush();
}

private String progressNotification(String progressToken, int progress, String message) {
    JsonObject notification = new JsonObject();
    notification.addProperty("jsonrpc", "2.0");
    notification.addProperty("method", "notifications/progress");
    JsonObject params = new JsonObject();
    params.addProperty("progressToken", progressToken);
    params.addProperty("progress", progress);
    params.addProperty("message", message);
    notification.add("params", params);
    return GSON.toJson(notification);
}
```

4. Add `handleMessage(String rawJson, OutputStream out)`:
```java
public void handleMessage(String rawJson, OutputStream out) throws IOException {
    try {
        JsonObject msg = JsonParser.parseString(rawJson).getAsJsonObject();
        if (!msg.has("id")) return;

        JsonElement requestId = msg.get("id");
        String method = msg.has("method") ? msg.get("method").getAsString() : null;
        JsonObject params = msg.has("params") ? msg.getAsJsonObject("params") : new JsonObject();

        if (!"tools/call".equals(method)) {
            // Non-tool calls: write as single SSE event with the JSON response
            Object result = dispatch(method, params);
            writeSseEvent(out, 1, successResponse(requestId, result));
            return;
        }

        String progressToken = extractProgressToken(params);
        int[] counter = {0};

        ProgressReporter reporter = (message) -> {
            try {
                counter[0]++;
                writeSseEvent(out, counter[0], progressNotification(progressToken, counter[0], message));
            } catch (IOException e) {
                // Client disconnected — silently ignore
            }
        };

        Object result = executeTool(params, reporter);
        counter[0]++;
        writeSseEvent(out, counter[0], successResponse(requestId, result));

    } catch (Exception ex) {
        try {
            JsonObject msg = JsonParser.parseString(rawJson).getAsJsonObject();
            if (msg.has("id")) {
                int code = (ex instanceof NoSuchMethodException) ? -32601 : -32603;
                writeSseEvent(out, 1, errorResponse(msg.get("id"), code, ex.getMessage()));
            }
        } catch (Exception ignored) {}
    }
}
```

5. Refactor `executeTool` to accept `ProgressReporter` and eliminate duplication. The existing `executeTool(JsonObject params)` should delegate to the new version:

```java
private Object executeTool(JsonObject params) {
    return executeTool(params, message -> {});
}

private Object executeTool(JsonObject params, ProgressReporter reporter) {
    String toolName = params.get("name").getAsString();
    JsonObject toolArgs = params.has("arguments") ? params.getAsJsonObject("arguments") : new JsonObject();

    try {
        return registry.callTool(toolName, toolArgs, reporter);
    } catch (Exception ex) {
        Map<String, Object> errorResult = new LinkedHashMap<>();
        errorResult.put("content", List.of(Map.of("type", "text", "text", "Error: " + ex.getMessage())));
        errorResult.put("isError", true);
        return errorResult;
    }
}
```

Update `handleMessage(String, OutputStream)` to call `executeTool(params, reporter)` instead of a separate method.

6. Add `import java.io.IOException; import java.io.OutputStream;` and `import uk.l3si.eclipse.mcp.tools.ProgressReporter;`.

- [ ] **Step 4: Run tests**

Run: `cd eclipse.mcp.server && mvn test -q`
Expected: All pass. The `protocolVersionIs2025` test now expects `2025-03-26`. Update the existing `initializeReturnsProtocolInfo` test to expect `"2025-03-26"` instead of `"2024-11-05"`.

- [ ] **Step 5: Commit**

```
feat: add SSE streaming support to McpProtocolHandler
```

---

### Task 4: McpHttpServer SSE response mode

**Files:**
- Modify: `src/main/java/uk/l3si/eclipse/mcp/McpHttpServer.java:45-73`

- [ ] **Step 1: Implement SSE routing in handleRequest**

Update `handleRequest` in `McpHttpServer.java`. The logic:
1. Parse the request body.
2. Check if it's a `tools/call` with `_meta.progressToken` AND `Accept` includes `text/event-stream`.
3. If yes: set `Content-Type: text/event-stream`, `Cache-Control: no-cache`, send 200 with length 0 (chunked), call `protocolHandler.handleMessage(body, outputStream)`.
4. If no: use existing plain JSON path.

```java
private void handleRequest(HttpExchange exchange) throws IOException {
    try {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }

        String requestBody;
        try (var input = exchange.getRequestBody()) {
            requestBody = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        if (shouldStreamSse(requestBody, exchange)) {
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.getResponseHeaders().set("Cache-Control", "no-cache");
            exchange.sendResponseHeaders(200, 0);
            try (var output = exchange.getResponseBody()) {
                protocolHandler.handleMessage(requestBody, output);
            }
            return;
        }

        String responseJson = protocolHandler.handleMessage(requestBody);

        if (responseJson == null) {
            exchange.sendResponseHeaders(202, -1);
            return;
        }

        byte[] responseBytes = responseJson.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, responseBytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(responseBytes);
        }
    } finally {
        exchange.close();
    }
}

private boolean shouldStreamSse(String requestBody, HttpExchange exchange) {
    try {
        String accept = exchange.getRequestHeaders().getFirst("Accept");
        if (accept == null || !accept.contains("text/event-stream")) {
            return false;
        }
        var msg = com.google.gson.JsonParser.parseString(requestBody).getAsJsonObject();
        if (!"tools/call".equals(msg.has("method") ? msg.get("method").getAsString() : null)) {
            return false;
        }
        var params = msg.has("params") ? msg.getAsJsonObject("params") : null;
        if (params == null || !params.has("_meta")) {
            return false;
        }
        return params.getAsJsonObject("_meta").has("progressToken");
    } catch (Exception e) {
        return false;
    }
}
```

- [ ] **Step 2: Run tests**

Run: `cd eclipse.mcp.server && mvn test -q`
Expected: All pass.

- [ ] **Step 3: Commit**

```
feat: add SSE response mode to McpHttpServer
```

---

### Task 5: ProjectBuilder progress reporting

**Files:**
- Modify: `src/main/java/uk/l3si/eclipse/mcp/core/tools/ProjectBuilder.java`
- Modify: `src/test/java/uk/l3si/eclipse/mcp/core/tools/ProjectBuilderTest.java`

- [ ] **Step 1: Write test for progress reporting in doCleanAndBuild**

In `ProjectBuilderTest.java`, add:

```java
@Test
void doCleanAndBuildReportsProgress() throws Exception {
    IProject projA = mockProject("A", true, true);
    IProject projB = mockProject("B", true, true);
    List<String> builtProjects = new ArrayList<>();
    IProgressMonitor monitor = new NullProgressMonitor();
    var messages = new ArrayList<String>();

    try (MockedStatic<Job> jobMock = mockStatic(Job.class);
         MockedStatic<ResourcesPlugin> rsMock = mockStatic(ResourcesPlugin.class)) {
        IJobManager jobManager = mock(IJobManager.class);
        jobMock.when(Job::getJobManager).thenReturn(jobManager);

        ProjectBuilder.doCleanAndBuild(new IProject[]{projA, projB}, builtProjects, monitor, messages::add);

        // Should report refresh, clean, build for each project
        assertTrue(messages.stream().anyMatch(m -> m.contains("Refreshing") && m.contains("A")));
        assertTrue(messages.stream().anyMatch(m -> m.contains("Cleaning") && m.contains("A")));
        assertTrue(messages.stream().anyMatch(m -> m.contains("Building") && m.contains("A")));
        assertTrue(messages.stream().anyMatch(m -> m.contains("Refreshing") && m.contains("B")));
        assertTrue(messages.stream().anyMatch(m -> m.contains("Building") && m.contains("B")));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd eclipse.mcp.server && mvn test -q -pl . -Dtest=ProjectBuilderTest#doCleanAndBuildReportsProgress`
Expected: FAIL — `doCleanAndBuild` doesn't accept ProgressReporter.

- [ ] **Step 3: Add ProgressReporter to ProjectBuilder methods**

Add `ProgressReporter` parameter to `doCleanAndBuild`, `cleanAndBuild`, and `refreshAndBuild`. Keep backward-compatible overloads for existing callers:

In `doCleanAndBuild`:
```java
static void doCleanAndBuild(IProject[] projects, List<String> builtProjects,
                            IProgressMonitor monitor, ProgressReporter progress) throws Exception {
    for (IProject project : projects) {
        if (project.isOpen()) {
            progress.report("Refreshing " + project.getName() + "...");
            project.refreshLocal(IResource.DEPTH_INFINITE, monitor);
        }
    }
    for (IProject project : projects) {
        if (project.isOpen()) {
            progress.report("Cleaning " + project.getName() + "...");
            project.build(IncrementalProjectBuilder.CLEAN_BUILD, monitor);
        }
    }
    Job.getJobManager().join(ResourcesPlugin.FAMILY_AUTO_BUILD, null);
    for (IProject project : projects) {
        if (project.isOpen()) {
            progress.report("Building " + project.getName() + "...");
            project.build(IncrementalProjectBuilder.FULL_BUILD, monitor);
            builtProjects.add(project.getName());
        }
    }
    Job.getJobManager().join(ResourcesPlugin.FAMILY_AUTO_BUILD, null);
}

```

Remove the old 3-arg `doCleanAndBuild` — no overload. Update existing tests to pass `message -> {}` as the 4th argument.

Similarly update `cleanAndBuild` — replace the old signature, no overload:
```java
static List<String> cleanAndBuild(List<String> projectNames, ProgressReporter progress) throws Exception { ... }
```

And `refreshAndBuild` — this method has two branches (named projects vs. all projects) and uses `SubMonitor` internally. Replace signature to require `ProgressReporter` and report before each refresh/build step:
```java
static List<String> refreshAndBuild(List<String> projectNames, ProgressReporter progress) throws Exception {
    // ... inside the Job.run():
    // In the named-projects branch, before each refresh:
    progress.report("Refreshing " + name + "...");
    // Before each build:
    progress.report("Building " + name + "...");
    // In the all-projects branch, same pattern per project
}
```

Similarly update `TestResultsHelper.collect`, `waitAndCollect`, and `waitForCompletion` — replace signatures to require `ProgressReporter`, no overloads.

Update all callers to pass `ProgressReporter` (or `message -> {}` where unused). Update existing tests that mock or call these methods to use the new signatures.

- [ ] **Step 4: Run tests**

Run: `cd eclipse.mcp.server && mvn test -q`
Expected: All pass — existing tests use the no-arg overloads, new test verifies progress.

- [ ] **Step 5: Commit**

```
feat: add progress reporting to ProjectBuilder
```

---

### Task 6: RunTestTool progress reporting

**Files:**
- Modify: `src/main/java/uk/l3si/eclipse/mcp/core/tools/RunTestTool.java`
- Modify: `src/main/java/uk/l3si/eclipse/mcp/core/tools/TestLaunchHelper.java`
- Modify: `src/main/java/uk/l3si/eclipse/mcp/core/tools/TestResultsHelper.java`

- [ ] **Step 1: Update execute in RunTestTool**

In `RunTestTool.java`, the `execute` method already has the new signature `execute(Args, ProgressReporter)` from Task 1. Update its body to use `progress`:

```java
@Override
public Object execute(Args args, ProgressReporter progress) throws Exception {
    progress.report("Waiting for previous test...");
    if (!RUN_LOCK.tryAcquire(LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
        throw new IllegalStateException(
                "Another run_test call is already in progress and did not complete within "
                + LOCK_TIMEOUT_SECONDS + " seconds. "
                + "Use 'terminate' to stop the running test, then retry.");
    }
    try {
        return doExecute(args, progress);
    } finally {
        RUN_LOCK.release();
    }
}
```

Update `doExecute` to pass `progress` to `ProjectBuilder.refreshAndBuild` and `TestLaunchHelper.launchTest`:
```java
List<String> builtProjects = ProjectBuilder.refreshAndBuild(refreshProjects, progress);
// ...
progress.report("Checking errors...");
// ...
progress.report("Launching " + className.substring(className.lastIndexOf('.') + 1) + "...");
LaunchTestResult launchResult = TestLaunchHelper.launchTest(configName, className, methodName, projectName, mode, debugContext, progress);
```

- [ ] **Step 2: Add ProgressReporter to TestLaunchHelper.launchTest**

Add `ProgressReporter progress` parameter. Forward it to `TestResultsHelper.waitAndCollect`:

```java
TestRunResult testResults = TestResultsHelper.waitAndCollect(launchResult[0], progress);
```

- [ ] **Step 3: Add per-test streaming to TestResultsHelper.waitAndCollect**

Add `ProgressReporter progress` parameter to `waitAndCollect`. In the `waitForCompletion` poll loop, track which tests have been reported and emit new ones:

```java
static TestRunResult waitAndCollect(ILaunch launch, ProgressReporter progress) throws InterruptedException {
    JUnitModel model = JUnitCorePlugin.getModel();
    TestRunSession session = findSession(model, launch);
    if (session == null) return null;

    waitForCompletion(session, launch, progress);
    return buildResult(session);
}
```

In `waitForCompletion`, add a `Set<String> reported` to track already-reported tests. On each poll iteration, walk the session tree for completed `ITestCaseElement`s and report new ones:

```java
private static void waitForCompletion(TestRunSession session, ILaunch launch, ProgressReporter progress) throws InterruptedException {
    if (launch == null) return;

    Set<String> reported = new HashSet<>();
    long terminatedAt = -1;
    while (isStillRunning(session)) {
        reportNewTestResults(session, reported, progress);
        if (!launch.isTerminated()) {
            // keep waiting
        } else {
            if (terminatedAt < 0) terminatedAt = System.currentTimeMillis();
            if (System.currentTimeMillis() - terminatedAt > POST_TERMINATION_GRACE_MS) break;
        }
        Thread.sleep(POLL_INTERVAL_MS);
    }
    // Final sweep for any tests completed in last iteration
    reportNewTestResults(session, reported, progress);
}
```

The `reportNewTestResults` method:
```java
private static void reportNewTestResults(ITestRunSession session, Set<String> reported, ProgressReporter progress) {
    reportNewTestResults((ITestElementContainer) session, reported, progress);
}

private static void reportNewTestResults(ITestElementContainer container, Set<String> reported, ProgressReporter progress) {
    for (ITestElement child : container.getChildren()) {
        if (child instanceof ITestCaseElement testCase) {
            String key = testCase.getTestClassName() + "#" + testCase.getTestMethodName();
            if (reported.contains(key)) continue;
            Result result = testCase.getTestResult(false);
            if (result == Result.UNDEFINED) continue; // not finished yet
            reported.add(key);
            progress.report(formatTestProgress(testCase, result));
        } else if (child instanceof ITestElementContainer nested) {
            reportNewTestResults(nested, reported, progress);
        }
    }
}

private static String formatTestProgress(ITestCaseElement testCase, Result result) {
    String method = testCase.getTestMethodName();
    double elapsed = testCase.getElapsedTimeInSeconds();
    String time = Double.isNaN(elapsed) ? "" : " (" + Math.round(elapsed * 10.0) / 10.0 + "s)";

    if (result == Result.OK) {
        return "PASSED: " + method + time;
    } else if (result == Result.IGNORED) {
        return "SKIPPED: " + method;
    }
    // FAILURE or ERROR
    String prefix = result == Result.ERROR ? "ERROR: " : "FAILED: ";
    FailureTrace trace = testCase.getFailureTrace();
    if (trace != null && trace.getTrace() != null) {
        String firstLine = trace.getTrace().split("\n")[0].trim();
        return prefix + method + " — " + firstLine;
    }
    return prefix + method + time;
}
```

No backward-compatible overloads — replace the old signatures entirely. Update all callers.

Make `formatTestProgress` package-visible (not private) for testability.

- [ ] **Step 4: Write tests for formatTestProgress**

Create or add to `src/test/java/uk/l3si/eclipse/mcp/core/tools/TestResultsHelperProgressTest.java`:

```java
package uk.l3si.eclipse.mcp.core.tools;

import org.eclipse.jdt.junit.model.ITestCaseElement;
import org.eclipse.jdt.junit.model.ITestElement.FailureTrace;
import org.eclipse.jdt.junit.model.ITestElement.Result;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestResultsHelperProgressTest {

    @Test
    void formatPassedTest() {
        ITestCaseElement testCase = mock(ITestCaseElement.class);
        when(testCase.getTestMethodName()).thenReturn("testLogin");
        when(testCase.getElapsedTimeInSeconds()).thenReturn(1.23);
        String result = TestResultsHelper.formatTestProgress(testCase, Result.OK);
        assertEquals("PASSED: testLogin (1.2s)", result);
    }

    @Test
    void formatFailedTest() {
        ITestCaseElement testCase = mock(ITestCaseElement.class);
        when(testCase.getTestMethodName()).thenReturn("testLogout");
        when(testCase.getElapsedTimeInSeconds()).thenReturn(Double.NaN);
        FailureTrace trace = mock(FailureTrace.class);
        when(trace.getTrace()).thenReturn("AssertionError: expected 200\n\tat com.example.Test.run");
        when(testCase.getFailureTrace()).thenReturn(trace);
        String result = TestResultsHelper.formatTestProgress(testCase, Result.FAILURE);
        assertEquals("FAILED: testLogout — AssertionError: expected 200", result);
    }

    @Test
    void formatSkippedTest() {
        ITestCaseElement testCase = mock(ITestCaseElement.class);
        when(testCase.getTestMethodName()).thenReturn("testDisabled");
        when(testCase.getElapsedTimeInSeconds()).thenReturn(Double.NaN);
        String result = TestResultsHelper.formatTestProgress(testCase, Result.IGNORED);
        assertEquals("SKIPPED: testDisabled", result);
    }
}
```

- [ ] **Step 5: Run tests**

Run: `cd eclipse.mcp.server && mvn test -q`
Expected: All pass.

- [ ] **Step 6: Commit**

```
feat: add progress reporting to run_test (per-stage and per-test)
```

---

### Task 7: CleanBuildTool progress reporting

**Files:**
- Modify: `src/main/java/uk/l3si/eclipse/mcp/core/tools/CleanBuildTool.java`
- Modify: `src/test/java/uk/l3si/eclipse/mcp/core/tools/CleanBuildToolTest.java`

- [ ] **Step 1: Write test for CleanBuildTool with progress**

In `CleanBuildToolTest.java`, add:

```java
@Test
void executeReportsProgressViaReporter() throws Exception {
    var messages = new java.util.ArrayList<String>();
    try (MockedStatic<ProjectBuilder> mocked = mockStatic(ProjectBuilder.class)) {
        mocked.when(() -> ProjectBuilder.cleanAndBuild(any(), any()))
                .thenAnswer(invocation -> {
                    ProgressReporter progress = invocation.getArgument(1);
                    progress.report("Refreshing projA...");
                    progress.report("Building projA...");
                    return List.of("projA");
                });

        CleanBuildTool tool = new CleanBuildTool();
        JsonObject args = new JsonObject();
        tool.execute(new Args(args), messages::add);

        assertEquals(2, messages.size());
        assertTrue(messages.get(0).contains("Refreshing"));
        assertTrue(messages.get(1).contains("Building"));
    }
}
```

Add required imports: `import uk.l3si.eclipse.mcp.tools.ProgressReporter;`

- [ ] **Step 2: Run test to verify it fails**

Run: `cd eclipse.mcp.server && mvn test -q -pl . -Dtest=CleanBuildToolTest#executeReportsProgressViaReporter`
Expected: FAIL — `CleanBuildTool` doesn't override two-arg execute.

- [ ] **Step 3: Update execute in CleanBuildTool to use progress**

The `execute` method already has the new signature from Task 1. Update its body:

```java
@Override
public Object execute(Args args, ProgressReporter progress) throws Exception {
    List<String> projectNames = args.getStringList("projects");
    List<String> builtProjects = ProjectBuilder.cleanAndBuild(projectNames, progress);
    return CleanBuildResult.builder().projects(builtProjects).build();
}
```

- [ ] **Step 4: Run tests**

Run: `cd eclipse.mcp.server && mvn test -q`
Expected: All pass.

- [ ] **Step 5: Commit**

```
feat: add progress reporting to clean_build
```

---

### Task 8: GetTestResultsTool keepalive and EvaluateExpressionTool semaphore progress

**Files:**
- Modify: `src/main/java/uk/l3si/eclipse/mcp/core/tools/GetTestResultsTool.java`
- Modify: `src/main/java/uk/l3si/eclipse/mcp/core/tools/TestResultsHelper.java`
- Modify: `src/main/java/uk/l3si/eclipse/mcp/debugging/tools/EvaluateExpressionTool.java`

- [ ] **Step 1: Update GetTestResultsTool to use progress**

The `execute` method already has the new signature from Task 1. Update its body to pass reporter to `TestResultsHelper.collect` for `wait=true`:

```java
@Override
public Object execute(Args args, ProgressReporter progress) throws Exception {
    checkNoActiveDebugLaunch();
    String className = args.getString("class");
    String methodName = args.getString("method");
    if (methodName != null && className == null) {
        throw new IllegalArgumentException("'class' is required when 'method' is specified.");
    }
    if (methodName != null) {
        String trace = TestResultsHelper.getFailureTrace(className, methodName);
        if (trace == null) {
            throw new IllegalArgumentException(
                    "No failure trace found for " + className + "#" + methodName
                    + ". Make sure a test run has completed and this test actually failed.");
        }
        return Map.of("class", className, "method", methodName, "trace", trace);
    }
    boolean wait = args.getBoolean("wait");
    TestRunResult result = TestResultsHelper.collect(wait, progress);
    if (result == null) {
        throw new IllegalStateException("No test runs found. Run a JUnit launch configuration first.");
    }
    return result;
}
```

In `TestResultsHelper.collect(boolean wait, ProgressReporter progress)`, emit `"Waiting for test to complete..."` every ~10 seconds during the poll loop. Add a `lastReportTime` tracker in `waitForCompletion`.

- [ ] **Step 2: Update EvaluateExpressionTool to report semaphore wait**

The `execute` method already has the new signature from Task 1. Add `progress.report("Waiting for previous evaluation...")` before `EVAL_LOCK.tryAcquire`:

```java
@Override
public Object execute(Args args, ProgressReporter progress) throws Exception {
    // ... existing validation ...
    progress.report("Waiting for previous evaluation...");
    if (!EVAL_LOCK.tryAcquire(EVAL_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
        throw new IllegalStateException("Timed out waiting for a previous evaluation to complete.");
    }
    // ... rest of method unchanged ...
}
```

- [ ] **Step 3: Run tests**

Run: `cd eclipse.mcp.server && mvn test -q`
Expected: All pass.

- [ ] **Step 4: Commit**

```
feat: add progress keepalive to get_test_results and evaluate_expression
```

---

### Task 9: Version bump, changelog, and final verification

**Files:**
- Modify: `eclipse.mcp.server/META-INF/MANIFEST.MF` — bump version
- Modify: `pom.xml` — bump parent version
- Modify: `eclipse.mcp.server/pom.xml` — bump child version
- Modify: `CHANGELOG.md` — add entry

- [ ] **Step 1: Bump version to 0.82.0**

Update version in `META-INF/MANIFEST.MF`, `pom.xml` (parent), and `eclipse.mcp.server/pom.xml` (child) from `0.81.0` to `0.82.0`.

- [ ] **Step 2: Add changelog entry**

Add to top of CHANGELOG.md:

```markdown
## 0.82.0

- **Streamable HTTP with progress notifications** — long-running tools (`run_test`, `clean_build`, `get_test_results`) now stream progress events via SSE, preventing MCP client timeouts. Individual test pass/fail results are streamed in real-time during test execution.
- Bump MCP protocol version to `2025-03-26`
```

- [ ] **Step 3: Run full test suite**

Run: `cd eclipse.mcp.server && mvn test -q`
Expected: All pass.

- [ ] **Step 4: Commit**

```
0.82.0: add Streamable HTTP with progress notifications for long-running tools
```
