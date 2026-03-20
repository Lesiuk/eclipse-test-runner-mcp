# Streamable HTTP with Progress Notifications

## Problem

Long-running tools (`run_test`, `clean_build`) block for minutes. Claude Code's MCP client times out after ~60 seconds of silence, causing the tool call to fail. The test keeps running in Eclipse but the LLM loses the connection and falls back to polling `get_test_results`.

## Solution

Upgrade the MCP transport to support Streamable HTTP with SSE responses. Long-running tools send `notifications/progress` events during execution. Each progress event resets the client's 60-second timeout, allowing tool calls of any duration. Per-test pass/fail results are streamed as they complete.

## MCP Protocol

- Bump protocol version from `2024-11-05` to `2025-03-26` (in `McpProtocolHandler.serverCapabilities()`)
- No new server capabilities required — progress is opt-in via `_meta.progressToken`
- No new endpoints — keep single `POST /mcp`
- `Mcp-Session-Id` header: intentionally omitted — this is a local-only server (bound to `127.0.0.1`) with a single client, so session multiplexing is not needed
- Protocol version negotiation: not needed — Claude Code is the primary client and supports `2025-03-26`

## Transport Layer

### McpHttpServer

`handleRequest` gains two response modes:

**Plain JSON (unchanged):** For `initialize`, `tools/list`, `ping`, and `tools/call` without `progressToken`. Returns `Content-Type: application/json` with buffered response.

**SSE stream:** For `tools/call` when `params._meta.progressToken` is present AND the client's `Accept` header includes `text/event-stream`. Returns `Content-Type: text/event-stream` with chunked encoding (`sendResponseHeaders(200, 0)`). Events are flushed immediately after each write. If `progressToken` is present but `Accept` does not include `text/event-stream`, fall back to plain JSON (progress events are silently dropped).

### SSE Event Format

Each SSE event uses `event: message` and includes an `id:` field (monotonic counter) per the MCP spec:

```
id: 1
event: message
data: {"jsonrpc":"2.0","method":"notifications/progress","params":{"progressToken":"t1","progress":1,"message":"Building backend..."}}

id: 2
event: message
data: {"jsonrpc":"2.0","id":1,"result":{...}}

```

The `total` field is omitted when unknown (e.g., during build stages). Once the test session starts and `ITestRunSession` provides a test count, `total` can be populated for the per-test progress events.

Zero progress events before the final response is valid — this happens for fast tools or non-streaming tools that happen to receive a `progressToken`.

### McpProtocolHandler

Signature changes from:
```java
String handleMessage(String rawJson)
```
to:
```java
String handleMessage(String rawJson)                    // existing — plain JSON
void handleMessage(String rawJson, OutputStream out)    // new — SSE streaming
```

The handler extracts `_meta.progressToken` from request params. If present and the method is `tools/call`, it uses the SSE path. Otherwise, it delegates to the existing method and the caller writes the JSON response.

The handler wraps each `progress.report(message)` call in the `notifications/progress` envelope with auto-incrementing `progress` counter and the client's `progressToken`.

### Error Handling

If a tool throws during SSE streaming, the handler writes the JSON-RPC error response as an SSE event and closes the stream. The client receives a proper error.

If the HTTP status is already committed (200 sent) and the first write fails (client already disconnected), the error cannot be sent — this is inherent to streaming HTTP.

### Client Disconnect

If the client disconnects mid-stream, writing to the `OutputStream` throws `IOException`. The handler catches this and lets the tool execution complete naturally (the tool doesn't know about the disconnect). No error is logged — this is expected behavior.

### Threading

The `ProgressReporter` writes to the HTTP response `OutputStream` from whichever thread calls `report()`. For `ProjectBuilder`, this is the Eclipse Job thread (the HTTP handler thread is blocked on `job.join()`). For `TestResultsHelper` poll loops, this is the HTTP handler thread. Since build and test phases are sequential, only one thread writes at a time — no synchronization needed.

## Progress Callback

### ProgressReporter Interface

```java
@FunctionalInterface
public interface ProgressReporter {
    void report(String message);
}
```

### McpTool Interface

Add a default method — existing tools don't change:

```java
public interface McpTool {
    String getName();
    String getDescription();
    InputSchema getInputSchema();
    Object execute(Args args) throws Exception;

    default Object execute(Args args, ProgressReporter progress) throws Exception {
        return execute(args);
    }
}
```

### ToolRegistry

Add a new overload:
```java
Object callTool(String name, JsonObject arguments, ProgressReporter progress)
```

The existing `callTool(name, arguments)` delegates to the new one with a no-op reporter. The new overload always calls the two-arg `execute(args, progress)`.

## Streaming Tools

### run_test

Progress events at each stage:

| Stage | Example message |
|-------|----------------|
| Semaphore wait | `"Waiting for previous test..."` |
| Refresh | `"Refreshing backend..."` |
| Build | `"Building backend..."` |
| Error check | `"Checking errors..."` |
| Launch | `"Launching FooIT..."` |
| Per-test pass | `"PASSED: testLogin (1.2s)"` |
| Per-test fail | `"FAILED: testLogout — AssertionError: expected 200"` |

Implementation: `RunTestTool.execute` accepts `ProgressReporter`, passes it to `ProjectBuilder.refreshAndBuild` and `TestLaunchHelper.launchTest`. For per-test streaming, the existing poll loop in `TestResultsHelper.waitAndCollect` diffs for newly completed `ITestCaseElement`s on each iteration and calls `progress.report()` for each.

Failure messages are kept to one line (first line of trace only). Full details come in the final result as today.

### clean_build

Progress per project through each phase:

| Stage | Example message |
|-------|----------------|
| Refresh | `"Refreshing core..."` |
| Clean | `"Cleaning core..."` |
| Build | `"Building core..."` |

Implementation: `ProjectBuilder.cleanAndBuild` and `ProjectBuilder.doCleanAndBuild` accept `ProgressReporter`.

### get_test_results (wait=true)

Periodic keepalive while waiting:

| Stage | Example message |
|-------|----------------|
| Waiting | `"Waiting for test to complete..."` |

Emitted every ~10 seconds during the poll loop in `TestResultsHelper.collect`.

### evaluate_expression

If blocked waiting for semaphore:

| Stage | Example message |
|-------|----------------|
| Semaphore | `"Waiting for previous evaluation..."` |

### All other tools

No change. They override only `execute(Args)`. The default two-arg method delegates to it. No-op reporter is passed.

## Token Efficiency

Progress messages are short status strings for keepalive and quick feedback. No verbose descriptions, no stack traces in progress events. Full details remain in the final result only.

The `notifications/progress` JSON-RPC envelope is ~100 bytes per event — minimal overhead.

## Files Changed

| File | Change |
|------|--------|
| `McpHttpServer` | SSE response mode for `tools/call` with progress token |
| `McpProtocolHandler` | Extract `_meta.progressToken`, SSE writing, new `handleMessage` overload |
| `ProgressReporter` | New interface (1 method) |
| `McpTool` | Add default `execute(Args, ProgressReporter)` |
| `ToolRegistry` | Pass `ProgressReporter` through `callTool` |
| `RunTestTool` | Override two-arg execute, pass reporter to helpers |
| `ProjectBuilder` | Accept `ProgressReporter` in `refreshAndBuild`, `cleanAndBuild`, `doCleanAndBuild` |
| `TestLaunchHelper` | Accept `ProgressReporter` in `launchTest` |
| `TestResultsHelper` | Accept `ProgressReporter` in `waitAndCollect` and `collect`, emit per-test results |
| `CleanBuildTool` | Override two-arg execute, pass reporter |
| `GetTestResultsTool` | Override two-arg execute, pass reporter for wait mode |
| `EvaluateExpressionTool` | Override two-arg execute, report semaphore wait |

## Testing

- `McpProtocolHandlerTest` — verify SSE response format, progress token extraction, error-during-stream handling
- `McpHttpServerTest` — verify Content-Type selection (JSON vs SSE)
- `RunTestToolTest` — verify progress events emitted at each stage
- `ProjectBuilderTest` — verify progress reported per project
- `TestResultsHelperTest` — verify per-test progress reporting
- `CleanBuildToolTest` — verify progress per project
- `GetTestResultsToolTest` — verify keepalive during wait
- `EvaluateExpressionToolTest` — verify semaphore wait reporting
