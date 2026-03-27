# Changelog

## 0.98.0

- **Harden expression evaluation against stack frame corruption** — added alternate try-catch wrappers using explicit `(Object)` casts with dual returns, providing a fallback when the primary variable-based wrappers fail to compile in the JDT AST engine. The evaluation now tries four wrapper strategies before falling back to unprotected direct evaluation. Also improved early frame validation to check `this` access (not just line number), reliably detecting invalid frames before the engine produces cryptic errors. Frame validity is now verified between each wrapper attempt.

## 0.99.0

- **Fix debug suspension detection in multi-threaded VMs** — in application servers (Quarkus, Spring Boot, etc.) background threads can briefly suspend and resume, overwriting the tracked breakpoint thread and causing `run_test` with `mode=debug` to miss the breakpoint hit. The SUSPEND event handler now preserves the tracked thread when it's still suspended, and `isSuspended()`/`resolveThread()` fall back to scanning all target threads directly when the tracked reference is lost.

## 0.97.0

- **Fix try-catch wrapper type inference for expression evaluation** — restructured the try-catch wrappers to use a single `Object`-typed local variable and one `return` statement instead of two `return` statements with different types. The JDT AST evaluation engine could not reconcile dual return types (e.g. `List<T>` vs `Throwable`), causing both wrappers to silently fail and fall through to unprotected direct evaluation, which corrupted the stack frame on exception.

## 0.96.0

- **Hint to check console output when no tests run** — when `run_test` completes with 0 tests executed and no failure, the result now includes a `hint` field directing the LLM to use `get_console_output` to check for runtime errors (e.g. class loading failures, missing dependencies).

## 0.95.0

- **Preserve exception details in `run_test` failure messages** — `extractMessage` now keeps message continuation lines (e.g. "Expected status code \<201\> but was \<500\>."), `Suppressed:` and `Caused by:` headers with their messages and application frames. Each cause section gets its own frame allowance. Previously these details were silently dropped, requiring a separate `get_test_results` call to see the actual error.

## 0.94.0

- **Void expression support for try-catch wrapping** — when the value-returning wrapper `return (expr)` fails to compile (void methods, setters, etc.), a second void-compatible wrapper `expr; return null;` is tried before falling back to direct evaluation. This means void expressions like `System.out.println()` or `obj.setField(value)` are also protected from frame invalidation on exception.

## 0.93.0

- **Safe expression evaluation via try-catch wrapping** — `evaluate_expression` now wraps expressions in `try { return (expr); } catch (Throwable) { return ex; }` before evaluating. If the expression throws (e.g. `StringIndexOutOfBoundsException`), the exception is caught inside the target VM so the stack frame stays valid for further evaluations. Falls back to direct evaluation for void expressions/statements. Exception details (type, message, filtered stack trace) are extracted from the caught Throwable and reported clearly.

## 0.92.0

- **Report `@BeforeClass` / `@BeforeAll` exceptions in test results** — `run_test` and `get_test_results` now capture suite-level setup failures that live on the `ITestSuiteElement` rather than on individual test cases. These appear as `<classSetup>` errors in the failures list with full stack traces.

## 0.91.0

- **Return compilation problems from `clean_build`** — `clean_build` now returns errors and warnings (grouped, same format as `get_problems`) after rebuilding, eliminating the need for a separate `get_problems` call. Extracted shared `collectProblems` method from `GetProblemsTool` for reuse.

## 0.90.0

- **Refactor test agent for maintainability** — split 562-line `MultiMethodRunner` into three focused classes: `ReflectionUtils` (hierarchy-aware method/field lookup), `MultiMethodFilterGenerator` (ASM-generated JUnit 4 Filter), and `MultiMethodRunner` (orchestration only). All 49 tests pass unchanged.

## 0.89.0

- **Fix multi-method producing 0 test results** — two bugs in the JUnit Platform path: (1) `Method.invoke()` varargs unwrapping caused `DiscoverySelector[]` to be spread into individual arguments instead of passed as one array, (2) `fRemoteTestRunner` was null because `loadTests()` was bypassed — now uses the runner instance directly

## 0.88.0

- **JUnit 6 multi-method support** — multi-method `run_test` now handles JUnit 6 loader natively (same JUnit Platform approach as JUnit 5). SWTBot already covered via JUnit 4 path.

## 0.87.0

- **Fix multi-method JUnit view display** — multi-method `run_test` now shows methods under a single class node in Eclipse's JUnit view instead of duplicating the class per method. Uses loader-specific APIs: JUnit 5 multi-selector `LauncherDiscoveryRequest`, JUnit 4 ASM-generated `Filter` subclass.

## 0.86.0

- **Fix method validation with empty method list** — skip validation when `IType.getMethods()` returns empty (e.g. binary types without source), letting Eclipse's test runner handle it instead of failing with an empty "Available methods:" message

## 0.85.0

- **Hint to re-run only failed tests** — `run_test` description now guides LLMs to use `method`/`methods` to re-run only failures, saving significant time for slow test suites (Selenium, SWTBot)

## 0.84.0

- **Multiple test methods in one call** — `run_test` now accepts a `methods` array to run multiple test methods in a single JVM launch, sharing the build step and JVM initialization for efficiency. Works with JUnit 3/4/5/6 and SWTBot. Uses a bundled Java agent that intercepts Eclipse's RemoteTestRunner to execute only the specified methods. Requires Eclipse 2019-06 or newer.

## 0.83.0

- **Debug mode returns test results on termination** — `run_test` in debug mode now collects and returns pass/fail results when the test finishes without hitting a breakpoint
- **Debug wait keepalive** — `run_test` (debug mode) and `step` (resume) send keepalive progress events every 60s while waiting for a breakpoint, preventing client timeouts
- Keepalive interval reduced from 10s to 60s to save tokens

## 0.82.0

- **Streamable HTTP with progress notifications** — long-running tools (`run_test`, `clean_build`, `get_test_results`) now stream progress events via SSE, preventing MCP client timeouts. Individual test pass/fail results are streamed in real-time during test execution.
- Bump MCP protocol version to `2025-03-26`

## v0.81.0

- **Fix NPE in projectNotFoundMessage** — handle null from `getProjects()` when listing available projects in error messages, preventing NullPointerException when workspace has no projects array

## v0.80.0

- **List available projects on "Project not found" errors** — when a tool receives an invalid project name, the error message now includes all open workspace projects so the LLM can self-correct without a separate `list_projects` call

## v0.79.0

- **Show helpful NullPointerException messages** — `evaluate_expression` now invokes `getMessage()` on exceptions in the target VM instead of reading the `detailMessage` field directly, capturing JDK 14+ helpful NPE messages (e.g. "Cannot invoke 'X.method()' because 'Y' is null")

## v0.78.0

- **Add `clean_build` tool** — performs a full clean and rebuild of Eclipse projects from scratch. Use when Eclipse gets heavily out of sync (stale errors, missing classes, broken incremental build state). Accepts an optional `projects` list to target specific projects; omit to clean/rebuild all open projects.

## v0.77.0

- **Fix error dialogs still appearing** — `ProjectBuilder` job now returns `OK_STATUS` instead of error status, preventing Eclipse from showing popup dialogs even when builds fail (errors are still properly returned to MCP caller)

## v0.76.0

- **Suppress error dialogs for MCP-initiated builds** — `ProjectBuilder` job now uses `setSystem(true)` instead of `setUser(true)`, preventing Eclipse from showing error popup windows (e.g. "Project not found") for failures that are already reported back to the MCP caller

## v0.75.0

- **Group compilation errors to reduce LLM token usage** — `run_test` and `get_problems` now deduplicate errors by (project, message), returning grouped entries with count and up to 3 example locations instead of repeating the same error for every affected file. Includes a one-line summary (e.g. "1191 errors (150 unique) in project 'backend'") and caps output at the top 10 groups.

## v0.74.0

- **Preserve console output across test runs** — `checkNoTestRunning()` and `cleanupLaunch()` no longer remove terminated launches from the launch manager, so Eclipse keeps the console output visible instead of clearing it on every `run_test` invocation

## v0.73.0

- **Fix `run_test` stale-launch deadlock** — `checkNoTestRunning()` now removes terminated JUnit launches from the launch manager before checking for running ones, preventing false "already running" errors caused by a race between JUnit session completion and `ILaunch.isTerminated()`
- **Clean up launches after normal test completion** — `launchTest()` now waits for `isTerminated()` and removes the launch from the manager when done, matching the cleanup that `terminate` already performed
- **Reduce `run_test` semaphore timeout from 15 minutes to 30 seconds** — prevents cascading pile-ups when MCP protocol times out and retries queue behind a long-held lock

## v0.72.0

- **Serialize `run_test` calls with a fair semaphore** — only one `run_test` executes at a time; concurrent callers wait up to 15 minutes instead of racing past the launch-in-progress check

## v0.71.0

- **Remove `fields` from variable output** — `VariableCollector` no longer dumps all field names for custom objects, eliminating massive response bloat for classes with many constants

## v0.70.0

- **Extract `VariableCollector` utility** — variable collection/formatting logic extracted from `ListVariablesTool` into a dedicated `VariableCollector` class in the `debugging` package, along with type-classification helpers (`isWellKnownType`, `isCollectionType`, `isMapType`) moved from `EvaluateExpressionTool`

## v0.69.0

- **Auto-include variables when debugger stops** — `step`, `get_debug_state`, and `run_test` (debug mode) now return all visible variables alongside the stop location, eliminating the need for a separate `list_variables` call after every breakpoint hit or step

## v0.68.0

- **`breakpoint action='clear'`** — remove all breakpoints in one call, returns the count of removed breakpoints

## v0.67.0

- **Deduplicate error messages in `evaluate_expression`** — when the same error appears multiple times (e.g. unresolved variable referenced twice), it is now shown only once

## v0.66.0

- **Remove `list_threads` tool** — rarely used; thread info is available via `get_debug_state`
- Tool count reduced from 17 to 16

## v0.65.0

- **Reject duplicate breakpoints** — `breakpoint action='set'` now checks for an existing breakpoint at the same class and line before creating a new one, preventing accidental duplicates

## v0.64.0

- **Source context in debug stop locations** — `step`, `get_debug_state`, and `run_test` (debug mode) now return ~5 lines of source code around the stop point, with the current line marked by `>`, eliminating the need to read the source file after every breakpoint hit or step
- **Merge `step` + `resume` into single `step` tool** — `resume` is now `step` with `action='resume'`; all actions support a `timeout` parameter and return terse reason values (`breakpoint`, `terminated`, `timeout`)
- **Merge `set_breakpoint` + `remove_breakpoint` + `list_breakpoints` into single `breakpoint` tool** — action-based dispatch (`set`, `remove`, `list`) following the same pattern as BPMN2 tools
- **Merge `get_failure_trace` into `get_test_results`** — pass `class` and `method` to get the full unabridged stack trace for a specific failure; no params returns the normal summary
- **Tool count reduced from 21 to 17** — fewer tool definitions means less LLM context overhead per request

## v0.63.0

- **Flat array format** — arrays now shown as `["a", "b", "c"]` instead of indexed objects, matching collection format
- **Better tool descriptions** — `evaluate_expression` now suggests multi-field inspection patterns; `list_variables` description updated for new output format
- Remove unused `ArrayElementInfo` model and `elements` field from results

## v0.62.0

- **Smart collection/map formatting in `evaluate_expression` and `list_variables`** — ArrayList returns `["item1", "item2", ...]` as a flat JSON array; HashMap returns `{"key": "value", ...}` as a JSON object; both include `length` and `truncated` for large collections (preview capped at 10 elements)
- **Auto-invoke `toString()` on custom objects** — shows meaningful representation (e.g. `Order{id=42, total=99.99}`) instead of `Order (id=1234)`
- No internal fields shown for collections, maps, or well-known JDK types

## v0.61.0

- **Skip fields for well-known JDK types** in `evaluate_expression` and `list_variables` — String, Integer, Boolean, BigDecimal, UUID, Date/Time types, etc. no longer show internal fields like `hash`, `coder`, `value`, reducing output noise for the LLM

## v0.60.0

- **Handle `InvalidStackFrameException` gracefully** in `list_variables`, `get_stack_trace`, and `evaluate_expression` — returns actionable guidance ("Use 'get_debug_state' to check current state") instead of raw JDI exception
- **Increase `resume` and `step` timeouts to 5 minutes** (was 30s) — matches `run_test` debug mode timeout, prevents premature timeouts on slow test runs

## v0.59.0

- **`resume` and `step` return test results on termination** — when resuming or stepping causes the debugged test to finish (no more breakpoints, test completes), the response now includes `testResults` with pass/fail counts, elapsed time, and failure details — same data as `run_test` and `get_test_results`, eliminating the need for a separate `get_test_results` call after debug sessions end
- Make `TestResultsHelper` public — reuse test result collection across core and debugging tools

## v0.58.0

- **Extract shared `StackTraceFilter`** — reuse framework-frame filtering between test results and expression evaluation; reads class/method fields directly from JDI `StackTraceElement` for smart filtering (skips JUnit, JDK, Eclipse internals)

## v0.57.0

- **`evaluate_expression` error includes stack trace** — invokes `getStackTrace()` on the target VM exception via JDI to show where the exception was thrown (up to 10 frames)

## v0.56.0

- **Restructure packages to consistent domain-based layout** — move `model/` → `core/model/`, `tools/impl/` → `core/tools/`; all three domains (`core`, `debugging`, `bpmn2`) now follow the same `{domain}/tools/` + `{domain}/model/` pattern; `tools/` retains only the shared framework (McpTool, Args, etc.)

## v0.55.0

- **Use direct JDI imports instead of reflection** for `InvocationException` unwrapping — cleaner code, same behavior

## v0.54.0

- **`evaluate_expression` error now includes exception message** — reads `detailMessage` field from the target VM exception (e.g. `NoSuchElementException: Cannot locate element` instead of just `NoSuchElementException thrown in target VM`)

## v0.53.0

- **`terminate` removes launches from manager after termination** — prevents `run_test` from seeing stale "still running" entries when called immediately after terminate

## v0.52.0

- **Increase `evaluate_expression` timeout to 60s** — both semaphore acquire and evaluation latch

## v0.51.0

- **Increase debug launch timeout to 5 minutes** — 30s was too short for apps that take time to start before hitting a breakpoint

## v0.50.0

- **Fix stale tool descriptions** — `list_variables` and `get_stack_trace` referenced removed `inspect_variable` tool; `run_test` now mentions debug mode waiting behavior

## v0.49.0

- **`run_test` in debug mode waits for first breakpoint** — instead of returning immediately with a "use get_debug_state" message, blocks until a breakpoint is hit or the launch terminates, returning stop reason and location directly

## v0.48.0

- **`step` converted from event listener to polling** — uses `DebugContext.waitForSuspendOrTerminate()` like `resume` and `get_debug_state`, eliminating `IDebugEventSetListener` registration
- **Extract shared poll/location/reason helpers into `DebugContext`** — `waitForSuspendOrTerminate()`, `getCurrentLocation()`, and `getSuspendReason()` eliminate duplication across `resume`, `step`, and `get_debug_state`

## v0.47.0

- **`resume` now blocks until next breakpoint or termination** — polls for suspend/terminate instead of returning immediately, returns stop reason and location (breakpoint, terminated, or timeout)
- **`evaluate_expression` unwraps InvocationException** — error messages now show the actual target-VM exception type (e.g. `NullPointerException thrown in target VM`) instead of the generic JDI wrapper
- **`evaluate_expression` returns JSON values as objects** — when a `java.lang.String` value contains valid JSON, it is returned as a parsed object instead of a double-encoded string

## v0.45.0

- **`evaluate_expression` now queues concurrent calls** — uses a fair semaphore so parallel evaluations wait for the previous one to finish instead of failing with "Cannot perform nested evaluations" (Eclipse JDT limitation)

## v0.44.0

- **Fix startup NPE** — restore separate `StartupHook` class for `IStartup`, since the extension registry creates its own instance where `start()` was never called. Bug introduced in v0.39.0.

## v0.43.0

- Fix startup NPE — workaround with instance delegation (superseded by v0.44.0)

## v0.42.0

- **`terminate` now waits for launches to fully die** — polls up to 10s after requesting termination, preventing race conditions where `run_test` would reject with "test already running" immediately after a successful terminate

## v0.41.0

- **Extract `ProjectBuilder`** — shared refresh & build logic used by `run_test` and `get_problems`, eliminating duplication

## v0.40.0

- **`get_problems` now refreshes and builds before returning** — ensures compilation markers are up-to-date after external file edits, matching the refresh & build pattern used by `run_test`

## v0.39.0

- **Consolidate startup** — delete `StartupHook`, `Activator` now implements `IStartup` directly
- **All tool registration in `ToolRegistry` constructor** — core, debugging, and BPMN2 tools registered in one place
- `DebugContext` self-registers its debug event listener in constructor

## v0.38.0

- **Remove `inspect_variable` tool** — its functionality is now merged into `evaluate_expression`, which already handles variable inspection via expression evaluation
- `evaluate_expression` now returns array element previews (first 10 elements) and truncation flags, matching the old `inspect_variable` output
- Debug tool count reduced from 11 to 10

## v0.37.0

- **Merge all three plugins into a single JAR** — core, debugging, and BPMN2 tools are now shipped together. No more separate `eclipse.mcp.server-debugging` or `eclipse.mcp.server-bpmn2` JARs to install.
- **BPMN2 tools disabled by default** — enable them in `Window > Preferences > MCP Server`. Debugging and core tools remain enabled by default.
- Simplify release workflow — single version tag (`vX.Y.Z`), single JAR artifact
- **Breaking:** existing users should remove the old debugging and BPMN2 JARs from `dropins/` before installing

## bpmn2 v0.4.0

- Compact tool set from 25 to 14 tools to reduce LLM context usage
- Merge add/remove pairs into single tools with `action` parameter: `bpmn2_variable`, `bpmn2_signal`, `bpmn2_import`, `bpmn2_item_definition`, `bpmn2_text_annotation`
- Merge flow tools into `bpmn2_flow` (action: add/update/remove)
- Merge start event, end event, extension point into `bpmn2_node` (action: add/update/remove, type: start_event/end_event/extension_point)
- Drop `add_` prefix from all tool names for consistency
- **Breaking:** all tool names changed — update any scripts or prompts referencing old names

## bpmn2 v0.3.0

- Add `bpmn2_add_text_annotation` — sticky-note diagram comments, optionally attached to nodes/flows
- Add `bpmn2_add_import` — process-level Java class imports for script tasks
- Add `bpmn2_add_item_definition` — standalone type definitions for `evaluatesToTypeRef`
- Add `JavaTypeValidator` — workspace-aware validation that service task interfaces/methods exist via JDT
- Validate `taskName` format and resolve interface/method against Eclipse workspace in `bpmn2_add_service_task` and `bpmn2_update_node`
- `bpmn2_get_process` now returns `imports` and `textAnnotations`

## v0.36.0

- Add MCP Server preference page (`Window > Preferences > MCP Server`) — enable/disable individual tools with per-group Select All / Deselect All buttons
- Tools grouped by module (Core, Debugging, BPMN2) with inline descriptions
- Disabled tools are hidden from `tools/list` and rejected on `tools/call`
- Settings persist across Eclipse restarts

## v0.35.0

- Clarify `get_failure_trace` guidance: run_test stack traces are usually sufficient, only use `get_failure_trace` when full untruncated trace is needed

## bpmn2 v0.2.0

- Replace monolithic `bpmn2_add_node` (14 params) with 7 domain-specific tools: `bpmn2_add_service_task`, `bpmn2_add_subflow_call`, `bpmn2_add_script_task`, `bpmn2_add_extension_point`, `bpmn2_add_gateway`, `bpmn2_add_start_event`, `bpmn2_add_end_event`
- Extract shared node-building code into `Bpmn2NodeHelper` utility
- Add `icon` update support to `bpmn2_update_node`
- Fix `displayName` to use `tns:displayName` attribute instead of metaData
- Improve tool descriptions with workflow hints (e.g. gateway → add_flow with conditions)
- Deduplicate `hasSignalEventDefinition` across AddFlowTool, RemoveNodeTool

## v0.34.0

- Remove redundant fields from responses to save LLM tokens: `testRunName`/`configName` from test results, `expression` from evaluate_expression, `variableCount` from list_variables
- Add workflow hints to `run_test` description: guide LLM to use `get_failure_trace` on failures and retry on compilation errors
- Add `inspect_variable` bracket notation hint to `list_variables` description for truncated arrays
- Improve `terminate` description: explain when to use it (stuck test, 'test already running' error, stopping debug sessions)

## v0.33.0

- Validate `mode` parameter in `run_test` against allowed values

## v0.32.0

- Reduce test session poll interval from 500ms to 100ms and check immediately before sleeping — fast tests no longer wait unnecessarily

## v0.31.0

- Round `elapsedSeconds` in test results to 2 decimal places

## v0.30.0

- Replace `refreshed`/`compiled` booleans in `run_test` response with `refreshedAndBuilt` — a list of project names that were actually refreshed and built, so the LLM knows which subset of the workspace has up-to-date code

## v0.29.0

- Compact `run_test` response — replace verbose `steps` array with `refreshed` and `compiled` boolean flags, remove redundant `success` and `errorCount` fields

## v0.28.0

- Fix: remove global lock from tool execution — `ToolRegistry.callTool()` was `synchronized`, blocking all concurrent tool calls while any single tool was running. When `run_test` blocked waiting for test completion, `get_test_results`, `list_test_runs`, `terminate` etc. would all queue behind the lock and timeout on the client side. The synchronized block now only covers tool lookup and parameter validation; execution runs lock-free.

## v0.27.0

- Add `find_references` tool — find all references to a Java class, method, or field across all open Eclipse workspace projects using Eclipse's semantic search engine
- Results grouped by file with line numbers and source line context
- Validates class and member existence, lists available members on mismatch
- Handles method overloads (searches all overloads together)

## v0.26.0

- Reject `run_test` in debug mode when no breakpoints are set — returns error directing to use `set_breakpoint` first

## Debug v0.6.5

- `get_debug_state` now defaults to `wait_for_suspend=true` — automatically blocks until a breakpoint is hit or the session ends, eliminating the need for LLMs to remember to pass the parameter
- Fix: track debug target from CREATE event — `get_debug_state` was returning `active: false` between launch and first breakpoint hit because `DebugContext` only learned about the target on SUSPEND
- Replace event listener + CountDownLatch wait mechanism with simple polling of `DebugContext` state (fixes `TypeError: fetch failed` caused by blocking on non-Eclipse threads)
- Add `Args.getBoolean(key, defaultValue)` overload for boolean parameters with non-false defaults

## v0.25.0

- Remove `launch_test` tool — `run_test` is now the single tool for running tests, eliminating ambiguity for LLM tool selection
- Remove `launch_test` reference from `run_test` and `list_test_configs` descriptions

## Debug v0.6.2

- `get_debug_state` now supports `wait_for_suspend` parameter — blocks until a breakpoint is hit or the session terminates, replacing inefficient polling loops
- Optional `timeout` parameter (default 30s) controls how long to wait before returning current state

## v0.24.0

- `launch_test` now rejects calls when breakpoints are set — returns error directing to use `run_test` with `mode='debug'` instead
- Reject unknown tool parameters — returns error listing valid parameters (prevents silent misuse like passing `mode` to `launch_test`)

## v0.23.0

- `get_test_results` now returns an error when a debug session is active — prevents deadlock when the test is paused at a breakpoint and can never complete

## v0.22.0

### Debug module (v0.6.1)

- Validate breakpoint locations using Eclipse's `ValidBreakpointLocationLocator` (same infrastructure as Eclipse's "Toggle Breakpoint" action)
- Automatically adjusts breakpoint to nearest valid executable line (returns `adjustedFrom` field when adjusted)
- Rejects lines with no valid breakpoint location with a clear error message
- Skips validation for binary/library classes without source — breakpoints still work
- Fix: use `ITypeRoot.getSource()` instead of `ICompilationUnit` for source retrieval (works for all type resolutions)

## v0.21.0

- Improve `get_failure_trace` tool description to discourage redundant calls — `run_test` and `get_test_results` already return condensed failure messages with exception type, message, and key stack frames

## v0.20.0

- Disable "Skip All Breakpoints" before launching tests in debug mode — ensures breakpoints are always hit

## v0.19.0

- `Args.requireString` now rejects blank strings (catches empty/whitespace-only inputs across all tools)
- `terminate` tool now throws when named config has no matching running launch
- Update `TerminateToolTest` to expect error on unmatched config name

### Debug module (v0.5.0)

- `inspect_variable` validates balanced brackets in path expressions
- `get_debug_state` and `list_threads` now report errors instead of silently ignoring them
- Add `error` field to `DebugStateResult` and `ThreadInfo` models

## v0.18.0

- Validate test method name exists before launching — reports available methods on mismatch

## v0.17.0

- Replace `Map`/`LinkedHashMap` return types with proper Lombok `@Builder` model classes across all tools
- Update `CoverageHelperTest` to use Gson serialization pattern consistent with other tests

### Debug module (v0.4.0)

- `ListVariablesTool` now returns `ListVariablesResult` model class instead of `LinkedHashMap`

## v0.16.0

- Suppress Eclipse focus stealing on breakpoint hit (`PREF_ACTIVATE_WORKBENCH` disabled on startup)
- Validate breakpoint condition syntax before setting (catches syntax errors early via JDT AST parser)
- Deduplicate Maven build config — shared surefire, JUnit, and Mockito config moved to parent pom
- Add `.DS_Store` to `.gitignore`

### Debug module (v0.3.0)

- New `list_variables` tool — lists all visible variables with types and values in the current stack frame
- Add breakpoint condition syntax validation in `BreakpointManager`
- Slim down `pom.xml` — inherits shared build config from parent

## v0.15.0

- Restructure into multi-module Maven project (core + debug)
  - `eclipse.mcp.server` — core test runner plugin (all existing tools)
  - `eclipse.mcp.server.debugging` — debug tools add-on (skeleton, requires core)
- Each release now produces two JARs
- ToolRegistry is now extensible — external plugins can register additional tools
- Rename Java packages to `uk.l3si.eclipse.mcp`

## v0.14.0

- Add code coverage support via EclEmma/JaCoCo
  - New `coverage` parameter on `run_test` — launches tests in coverage mode
  - New `get_coverage` tool — returns detailed per-method and per-line coverage for a source class
  - Waits for coverage analysis if still loading (like `get_test_results` waits for running tests)

## v0.13.0

- Initial release
