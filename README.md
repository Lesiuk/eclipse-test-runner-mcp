# Eclipse Test Runner MCP

[![License: GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-blue.svg)](LICENSE)
[![Java 17+](https://img.shields.io/badge/Java-17%2B-orange.svg)](https://openjdk.org/)
[![Eclipse 2024-12+](https://img.shields.io/badge/Eclipse-2024--12%2B-purple.svg)](https://eclipseide.org/)
[![MCP](https://img.shields.io/badge/MCP-HTTP-green.svg)](https://modelcontextprotocol.io/)

Eclipse IDE plugin that lets AI coding assistants ([Claude Code](https://docs.anthropic.com/en/docs/claude-code), [GitHub Copilot](https://github.com/features/copilot)) run JUnit tests, inspect results, check compilation errors, and read console output through the [Model Context Protocol](https://modelcontextprotocol.io/).

- **Run JUnit tests** — full build pipeline with refresh, build, and error checking
- **Code coverage** — per-line and per-method coverage via EclEmma/JaCoCo
- **Interactive debugging** — breakpoints, stepping, variable inspection, expression evaluation
- **Code intelligence** — find references to classes, methods, and fields across the workspace
- **Workspace diagnostics** — compilation errors, console output, project listing

## Installation

The plugin runs as an OSGi bundle inside Eclipse and exposes an HTTP endpoint on `127.0.0.1:5188`.

Each release contains two JARs, versioned independently:

| JAR | Required? | Description |
|-----|-----------|-------------|
| `eclipse.mcp.server_VERSION.jar` | **Yes** | Core plugin — test runner, coverage, workspace tools |
| `eclipse.mcp.server-debugging_VERSION.jar` | No | Debug tools add-on — breakpoints, stepping, variable inspection |

Both JARs are included in every release so you can always grab a compatible pair from a single page, even when only one module has changed. The version in the filename tells you what's new.

1. Download the latest JAR(s) from the [releases page](https://github.com/Lesiuk/eclipse-test-runner-mcp/releases/latest)
2. Drop them into `<eclipse-install>/dropins/`
3. Restart Eclipse

Only the core JAR is needed for test running and coverage. Install the debugging JAR alongside it to add breakpoint and debugger tools — including a `debug` launch mode on `run_test`.

## Connecting an AI Assistant

The server speaks MCP over HTTP at `http://127.0.0.1:5188/mcp`.

**Claude Code:**
```
claude mcp add eclipse --transport http http://127.0.0.1:5188/mcp -s user
```

**GitHub Copilot CLI:**

1. Enter interactive mode and type `/mcp add`
2. **Server Name** — enter `eclipse`
3. **Server Type** — select **HTTP**
4. **URL** — enter `http://127.0.0.1:5188/mcp`
5. Press <kbd>Ctrl</kbd>+<kbd>S</kbd> to save

## Tools

Parameters marked with **\*** are required. All others are optional.

#### Core (`eclipse.mcp.server`)

| Tool | Description |
|------|-------------|
| `run_test` | Full pipeline — refresh, build, check errors, launch test |
| `get_test_results` | Results from the most recent test run |
| `get_failure_trace` | Full stack trace for a single test failure |
| `get_coverage` | Per-line and per-method code coverage for a class |
| `list_test_configs` | All JUnit launch configurations in the workspace |
| `list_test_runs` | Active and recent JUnit launches |
| `list_projects` | All open projects in the workspace |
| `get_problems` | Compilation errors (and optionally warnings) |
| `get_console_output` | stdout/stderr from a launch |
| `terminate` | Stop running launches |
| `find_references` | Find all references to a class, method, or field |

#### Debugging add-on (`eclipse.mcp.server.debugging`)

| Tool | Description |
|------|-------------|
| `set_breakpoint` | Set a line breakpoint (with optional condition) |
| `remove_breakpoint` | Remove a breakpoint by ID |
| `list_breakpoints` | All Java line breakpoints in the workspace |
| `get_debug_state` | Check if debugger is active/suspended |
| `list_threads` | All threads in the debug session |
| `get_stack_trace` | Stack trace for a suspended thread |
| `list_variables` | List all visible variables in the current stack frame |
| `inspect_variable` | Inspect a variable (supports dot-paths and indexing) |
| `evaluate_expression` | Evaluate a Java expression in a suspended frame |
| `step` | Step over, into, or return |
| `resume` | Resume a suspended thread |

### Typical Session

```
list_test_configs          → discover available JUnit configurations
run_test                   → edit code, then refresh + build + run
run_test (mode=coverage)   → same, but with code coverage enabled
run_test (mode=debug)      → run with debugger (set breakpoints first)
get_test_results           → re-check results or wait for completion
get_failure_trace          → drill into a specific failure's full stack trace
get_coverage               → inspect per-line coverage for a source class
```

**Debug session example:**

```
set_breakpoint             → set breakpoint at a suspicious line
run_test (mode=debug)      → launch test with debugger attached
get_debug_state            → check if breakpoint was hit
list_variables             → see all variables in scope
inspect_variable           → examine variable values at the breakpoint
evaluate_expression        → evaluate a Java expression in context
step (action=over)         → step to the next line
resume                     → let the test finish
```

<details>
<summary><strong>Detailed tool reference</strong></summary>

#### Test Execution

**`run_test`** `(config*, class*, method, project, dependencies[], mode)` → `{steps, success, compilationErrors[], launchResult{status, totalTests, passed, failed, failures[]}}`

Full pipeline — refresh projects from disk, build (dependencies first, in order), check for compilation errors, then launch the test. Fails fast if compilation errors are found. Rejects the call if another test is already running. The `mode` parameter controls how the test launches: `run` (default), `coverage` (EclEmma/JaCoCo — use `get_coverage` afterwards), or `debug` (requires debug add-on — set breakpoints first).

Uses an existing launch configuration as a template — inheriting VM arguments, classpath, and environment — while overriding just the test target (class/method).

#### Test Results

**`get_test_results`** `(wait)` → `{testRunName, status, totalTests, passed, failed, errors, ignored, failures[]}`

Returns results from the most recent test run. Each failure includes the exception message and a compact stack trace: top 3 application frames plus all test-class frames, with framework internals filtered and remaining frames collapsed as `... N more`. Pass `wait=true` to block until a running test finishes.

**`get_failure_trace`** `(class*, method*)` → `{class, method, trace}`

Full untruncated stack trace for a single test failure. Use after reviewing `get_test_results` when the trimmed trace isn't sufficient.

**`get_coverage`** `(class*)` → `{class, summary{lineCoverage, branchCoverage, methodCoverage}, methods[{name, lineCoverage, branchCoverage, uncoveredLines[]}], lines[{line, status, branches}]}`

Detailed code coverage for a source class after a coverage run. Returns per-method coverage with uncovered line numbers, and per-line status (`COVERED`, `NOT_COVERED`, `PARTLY_COVERED`). Waits for coverage analysis to complete if still loading.

#### Workspace & Diagnostics

**`list_test_configs`** `()` → `{testConfigurations[{name, type}]}`

Enumerates all JUnit launch configurations — regular JUnit, JUnit Plug-in Test, and SWTBot.

**`list_test_runs`** `()` → `{testRuns[{name, mode, terminated}]}`

Shows active and recent JUnit launches with their running/terminated status.

**`list_projects`** `()` → `{projects[]}`

Names of all open projects in the workspace.

**`get_problems`** `(project, includeWarnings)` → `{errorCount, warningCount, errors[{project, file, line, message}], warnings[]}`

Java compilation errors from Eclipse's Problems view. Optionally includes warnings. Can be scoped to a single project.

**`get_console_output`** `(name)` → `{configName, terminated, stdout, stderr}`

Captures stdout and stderr from the most recent launch (or a specific one by name). Automatically falls back to reading the Eclipse Console view when stream monitor buffers have been cleared.

**`terminate`** `(name)` → `{terminated}`

Stops running launches. Optionally filtered by configuration name. Returns the number of launches terminated.

#### Code Intelligence

**`find_references`** `(class*, member)` → `{element, totalReferences, files[{class, references[{line, source}]}]}`

Find all references to a Java class, method, or field across all open workspace projects. Uses Eclipse's semantic search engine — finds actual usages, not just name matches. If `member` is omitted, finds references to the class itself. Handles method overloads (searches all overloads together). Results grouped by file with line numbers and source line context.

#### Debugging (requires debug add-on)

**`set_breakpoint`** `(class*, line*, condition)` → `{id, class, line, condition, enabled}`

Set a line breakpoint in a Java class. Optionally set a conditional expression — the debugger only suspends when the condition evaluates to true.

**`remove_breakpoint`** `(id*)` → `{removed, id}`

Remove a breakpoint by its ID.

**`list_breakpoints`** `()` → `{breakpoints[{id, class, line, condition, enabled, hitCount}]}`

List all Java line breakpoints in the workspace — including ones set manually in the Eclipse IDE.

**`get_debug_state`** `()` → `{active, suspended, thread, threadId, location{class, method, line, sourceName}, reason}`

Check whether a debug session is active and if a thread is suspended (e.g. at a breakpoint or after a step).

**`list_threads`** `()` → `{threads[{name, id, state, location}]}`

List all threads in the debug session with their state (running, suspended, terminated).

**`get_stack_trace`** `(thread_id)` → `{thread, frames[{index, class, method, line, sourceName}]}`

Get the stack trace for a suspended thread. Use the frame index with `inspect_variable` or `evaluate_expression` to work in a specific frame's context.

**`list_variables`** `(thread_id, frame_index)` → `{frame, variableCount, variables[{name, type, value, fields[], length, elements[], truncated}]}`

List all visible variables in the current stack frame with their types and shallow values. Shows local variables, method parameters, and `this` fields. For objects, shows field names; for arrays, shows length and first few elements. Use `inspect_variable` to drill deeper.

**`inspect_variable`** `(name*, thread_id, frame_index)` → `{name, type, value, fields[], elements[]}`

Inspect a variable in the current stack frame. Supports dot-path navigation (`obj.field`) and array indexing (`list[0]`). Objects show field names for drill-down; arrays show length and elements.

**`evaluate_expression`** `(expression*, thread_id, frame_index)` → `{expression, type, value, fields[]}`

Evaluate a Java expression in the context of a suspended stack frame. Can read values, call methods, or modify variables.

**`step`** `(action*, thread_id)` → `{action, thread, location{class, method, line, sourceName}}`

Step through code: `over` (next line), `into` (enter method call), or `return` (exit current method). Waits for the step to complete and returns the new location.

**`resume`** `(thread_id)` → `{resumed, thread}`

Resume a suspended thread.

</details>

## Building from Source

Requires Java 17+ and Maven 3.9+.

```
mvn package -B
```

Output JARs land in each plugin's `target/` directory. Copy to Eclipse `dropins/` and restart.

## [Changelog](CHANGELOG.md)

## License

[GPL-3.0](LICENSE)

## Acknowledgements

Inspired by [eclipse-mcp-server](https://github.com/maxmart/eclipse-mcp-server) by Max Martinsson.
