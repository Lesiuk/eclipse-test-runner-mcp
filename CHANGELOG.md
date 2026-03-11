# Changelog

## v0.22.0

### Debug module (v0.6.0)

- Validate breakpoint locations using Eclipse's `ValidBreakpointLocationLocator` (same infrastructure as Eclipse's "Toggle Breakpoint" action)
- Automatically adjusts breakpoint to nearest valid executable line (returns `adjustedFrom` field when adjusted)
- Rejects lines with no valid breakpoint location with a clear error message
- Skips validation for binary/library classes without source — breakpoints still work

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
