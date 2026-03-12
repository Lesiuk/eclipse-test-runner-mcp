# Changelog

## v0.26.0

- Reject `run_test` in debug mode when no breakpoints are set — returns error directing to use `set_breakpoint` first

## Debug v0.6.3

- `get_debug_state` now defaults to `wait_for_suspend=true` — automatically blocks until a breakpoint is hit or the session ends, eliminating the need for LLMs to remember to pass the parameter
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
