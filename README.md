# Eclipse Test Runner MCP

Eclipse IDE plugin that lets AI coding assistants ([Claude Code](https://docs.anthropic.com/en/docs/claude-code), [GitHub Copilot](https://github.com/features/copilot)) run JUnit tests, inspect results, check compilation errors, and read console output through the [Model Context Protocol](https://modelcontextprotocol.io/).

## Installation

The plugin runs as an OSGi bundle inside Eclipse and exposes an HTTP endpoint on `127.0.0.1:5188`.

1. Download the latest JAR from the [releases page](https://github.com/Lesiuk/eclipse-test-runner-mcp/releases/latest)
2. Copy it into `<eclipse-install>/dropins/`
3. Restart Eclipse

## Connecting an AI Assistant

The server speaks MCP over HTTP at `http://127.0.0.1:5188/mcp`.

**Claude Code:**
```
claude mcp add eclipse --transport http http://127.0.0.1:5188/mcp -s user
```

**GitHub Copilot CLI:** in interactive mode, run `/mcp add` → select HTTP → name: `eclipse` → URL: `http://127.0.0.1:5188/mcp`.

## Tools Reference

Parameters marked with **\*** are required. All others are optional.

### Test Execution

**`run_test`** `(config*, class*, method, project, dependencies[])` → `{steps, success, compilationErrors[], launchResult{status, totalTests, passed, failed, failures[]}}`

Full pipeline — refresh projects from disk, build (dependencies first, in order), check for compilation errors, then launch the test. Fails fast if compilation errors are found. Rejects the call if another test is already running.

**`launch_test`** `(config*, class*, method, project)` → `{config, project, class, method, testResults{…}}`

Runs a test immediately without refreshing or rebuilding. Intended for quick re-runs when source code hasn't changed since the last build. Also rejects if a test is already running.

Both tools use an existing launch configuration as a template — inheriting VM arguments, classpath, and environment — while overriding just the test target (class/method).

### Test Results

**`get_test_results`** `(wait)` → `{testRunName, status, totalTests, passed, failed, errors, ignored, failures[]}`

Returns results from the most recent test run. Each failure includes the exception message and a compact stack trace: top 3 application frames plus all test-class frames, with framework internals filtered and remaining frames collapsed as `... N more`. Pass `wait=true` to block until a running test finishes.

**`get_failure_trace`** `(class*, method*)` → `{class, method, trace}`

Full untruncated stack trace for a single test failure. Use after reviewing `get_test_results` when the trimmed trace isn't sufficient.

### Workspace & Diagnostics

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

### Typical Session

```
list_test_configs          → discover available JUnit configurations
run_test                   → edit code, then refresh + build + run
launch_test                → re-run without rebuild when code is unchanged
get_test_results           → re-check results or wait for completion
get_failure_trace          → drill into a specific failure's full stack trace
```

## Building from Source

Requires Java 17+ and Maven 3.9+.

```
mvn package -B
```

Output JAR lands in `target/`. Copy to Eclipse `dropins/` and restart.

## [Changelog](CHANGELOG.md)

## License

[GPL-3.0](LICENSE)

## Acknowledgements

Inspired by [eclipse-mcp-server](https://github.com/maxmart/eclipse-mcp-server) by Max Martinsson.
