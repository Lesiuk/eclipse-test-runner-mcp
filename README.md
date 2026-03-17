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

1. Download `eclipse.mcp.server_VERSION.jar` from the [releases page](https://github.com/Lesiuk/eclipse-test-runner-mcp/releases/latest)
2. Drop it into `<eclipse-install>/dropins/`
3. Restart Eclipse

All tools (core, debugging, BPMN2) are included in a single JAR. Debugging tools are enabled by default. BPMN2 tools are disabled by default — enable them in `Window > Preferences > MCP Server`.

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

#### Core

| Tool | Description |
|------|-------------|
| `run_test` | Full pipeline — refresh, build, check errors, launch test |
| `get_test_results` | Results from the most recent test run (pass class+method for full stack trace) |
| `get_coverage` | Per-line and per-method code coverage for a class |
| `list_test_configs` | All JUnit launch configurations in the workspace |
| `list_test_runs` | Active and recent JUnit launches |
| `list_projects` | All open projects in the workspace |
| `get_problems` | Compilation errors (and optionally warnings) |
| `get_console_output` | stdout/stderr from a launch |
| `terminate` | Stop running launches |
| `find_references` | Find all references to a class, method, or field |

#### Debugging

| Tool | Description |
|------|-------------|
| `breakpoint` | Manage breakpoints (action: set/remove/clear/list) |
| `get_debug_state` | Check if debugger is active/suspended |
| `get_stack_trace` | Stack trace for a suspended thread |
| `list_variables` | List all visible variables in the current stack frame |
| `evaluate_expression` | Evaluate a Java expression in a suspended frame |
| `step` | Step over, into, return, or resume |

#### BPMN2 (disabled by default)

| Tool | Description |
|------|-------------|
| `bpmn2_create_process` | Create a new BPMN2 process file with boilerplate XML |
| `bpmn2_get_process` | Read a BPMN2 file and return all nodes, flows, variables, and signals |
| `bpmn2_service_task` | Add a service task that calls a Java service method |
| `bpmn2_subflow_call` | Add a call activity that invokes another BPMN2 subprocess |
| `bpmn2_script_task` | Add a script task that executes inline Java code |
| `bpmn2_gateway` | Add an exclusive gateway for branching or merging |
| `bpmn2_node` | Add start/end event or extension point, update or remove a node (action: add/update/remove) |
| `bpmn2_flow` | Add, update, or remove a sequence flow (action: add/update/remove) |
| `bpmn2_variable` | Add or remove a process variable (action: add/remove) |
| `bpmn2_signal` | Add or remove a signal definition (action: add/remove) |
| `bpmn2_import` | Add or remove a Java class import (action: add/remove) |
| `bpmn2_item_definition` | Add or remove a type definition (action: add/remove) |
| `bpmn2_text_annotation` | Add or remove a diagram comment (action: add/remove) |
| `bpmn2_auto_layout` | Auto-layout all nodes and edges in the diagram |

### Typical Session

```
list_test_configs          → discover available JUnit configurations
run_test                   → edit code, then refresh + build + run
run_test (mode=coverage)   → same, but with code coverage enabled
run_test (mode=debug)      → run with debugger (set breakpoints first)
get_test_results           → re-check results or wait for completion
get_test_results (class+method) → drill into a specific failure's full stack trace
get_coverage               → inspect per-line coverage for a source class
```

**Debug session example:**

```
breakpoint (action=set)    → set breakpoint at a suspicious line
run_test (mode=debug)      → launch test with debugger attached
get_debug_state            → check if breakpoint was hit
list_variables             → see all variables in scope
evaluate_expression        → evaluate a Java expression in context
step (action=over)         → step to the next line
step (action=resume)       → let the test finish
```

**BPMN2 workflow example:**

```
bpmn2_create_process       → create a new .bpmn2 file with process boilerplate
bpmn2_variable (add)       → add processCommandFlow variable
bpmn2_import (add)         → import a utility class for use in script tasks
bpmn2_signal (add)         → define a signal for event-driven start events

bpmn2_node (add start_event)→ add the main start event
bpmn2_service_task         → call a Java service (validated against workspace)
bpmn2_gateway              → add a diverging gateway for conditional branching
bpmn2_script_task          → add inline Java logic
bpmn2_subflow_call         → delegate to another BPMN2 subprocess
bpmn2_node (add ext_point) → add a web extension point (human task)
bpmn2_gateway              → add a converging gateway to merge branches
bpmn2_node (add end_event) → add the end event

bpmn2_flow (add)           → connect nodes with sequence flows
bpmn2_flow (add+condition) → add conditional branch with Java expression
bpmn2_text_annotation (add)→ annotate a node with a diagram comment

bpmn2_auto_layout          → arrange all shapes and edges on the diagram
bpmn2_get_process          → verify the complete process structure
```

<details>
<summary><strong>Detailed tool reference</strong></summary>

#### Test Execution

**`run_test`** `(config*, class*, method, project, dependencies[], mode)` → `{steps, success, compilationErrors[], launchResult{status, totalTests, passed, failed, failures[]}}`

Full pipeline — refresh projects from disk, build (dependencies first, in order), check for compilation errors, then launch the test. Fails fast if compilation errors are found. Rejects the call if another test is already running. The `mode` parameter controls how the test launches: `run` (default), `coverage` (EclEmma/JaCoCo — use `get_coverage` afterwards), or `debug` (set breakpoints first).

Uses an existing launch configuration as a template — inheriting VM arguments, classpath, and environment — while overriding just the test target (class/method).

#### Test Results

**`get_test_results`** `(wait, class, method)` → `{status, totalTests, passed, failed, errors, ignored, failures[]}` or `{class, method, trace}`

Returns results from the most recent test run. Each failure includes the exception message and a compact stack trace: top 3 application frames plus all test-class frames, with framework internals filtered and remaining frames collapsed as `... N more`. Pass `wait=true` to block until a running test finishes. Pass `class` and `method` to get the full unabridged stack trace for a specific failure.

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

#### Debugging

**`breakpoint`** `(action*, class, line, condition, id)` → varies by action

Manage breakpoints. `action='set'` (requires `class`, `line`; optional `condition`): set a line breakpoint, returns `{id, class, line, condition, enabled}`. `action='remove'` (requires `id`): remove a breakpoint by its ID, returns `{removed, id}`. `action='clear'`: remove all breakpoints, returns `{removed}` (count). `action='list'`: list all Java line breakpoints in the workspace, returns `{breakpoints[{id, class, line, condition, enabled, hitCount}]}`.

**`get_debug_state`** `()` → `{active, suspended, thread, threadId, location{class, method, line, sourceName, source}, reason}`

Check whether a debug session is active and if a thread is suspended (e.g. at a breakpoint or after a step).

**`get_stack_trace`** `(thread_id)` → `{thread, frames[{index, class, method, line, sourceName}]}`

Get the stack trace for a suspended thread. Use the frame index with `evaluate_expression` to work in a specific frame's context.

**`list_variables`** `(thread_id, frame_index)` → `{frame, variableCount, variables[{name, type, value, fields[], length, elements[], truncated}]}`

List all visible variables in the current stack frame with their types and shallow values. Shows local variables, method parameters, and `this` fields. For objects, shows field names; for arrays, shows length and first few elements. Use `evaluate_expression` to inspect them.

**`evaluate_expression`** `(expression*, thread_id, frame_index)` → `{type, value, fields[]}`

Evaluate a Java expression in the context of a suspended stack frame. Can read values, call methods, or modify variables.

**`step`** `(action*, thread_id, timeout)` → `{action, thread, reason, location{class, method, line, sourceName, source}, terminated, testResults}`

Control debugger execution: `over` (next line), `into` (enter method call), `return` (exit current method), or `resume` (run until next breakpoint or termination). Returns the new location with source context. On termination, includes test results if available.

</details>

## Building from Source

Requires Java 17+ and Maven 3.9+.

```
mvn package -B
```

Output JAR lands in `eclipse.mcp.server/target/`. Copy to Eclipse `dropins/` and restart.

## [Changelog](CHANGELOG.md)

## License

[GPL-3.0](LICENSE)

## Acknowledgements

Inspired by [eclipse-mcp-server](https://github.com/maxmart/eclipse-mcp-server) by Max Martinsson.
