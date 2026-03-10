# Eclipse MCP Debug Tools — Design Spec

## Goal

Add 10 MCP tools to the `eclipse.mcp.server.debugging` plugin that give AI assistants full debugger control: set/remove line breakpoints, inspect suspended state, navigate threads and stack frames, inspect variables, evaluate expressions, and step through code.

## Tools

| Tool | Parameters | Returns |
|------|-----------|---------|
| `set_breakpoint` | `class*`, `line*`, `condition` | `{id, class, line, condition, enabled}` |
| `remove_breakpoint` | `id*` | `{removed: true}` |
| `list_breakpoints` | — | `{breakpoints: [{id, class, line, condition, enabled, hitCount}]}` |
| `get_debug_state` | — | `{active, suspended, thread, frame, location, reason}` or `{active: false}` |
| `list_threads` | — | `{threads: [{id, name, state, isSuspended, breakpointLocation}]}` |
| `get_stack_trace` | `thread_id` | `{thread, frames: [{index, class, method, line, sourceName}]}` |
| `inspect_variable` | `name`/`path`, `thread_id`, `frame_index` | `{name, type, value, fields[]}` or `{name, type, value, length, elements[]}` |
| `evaluate_expression` | `expression*`, `thread_id`, `frame_index` | `{type, value, fields[]}` |
| `step` | `action*` (over/into/return) | `{action, location}` waits for step to complete |
| `resume` | `thread_id` | `{resumed: true}` |

Parameters marked * are required. Others default to the current debug context.

## Architecture

### DebugContext — tracks active debug state

Singleton managed by DebugStartupHook. Listens for Eclipse debug events via `IDebugEventSetListener`.

**State tracked:**
- Current `IDebugTarget` (the debuggee process)
- Current `IThread` (thread that hit a breakpoint or completed a step)
- Current `IJavaStackFrame` (top frame of current thread)

**Events handled:**
- `SUSPEND` (breakpoint hit, step end) → update current thread/frame
- `RESUME` → mark thread as running, clear current frame
- `TERMINATE` → clear all state

**Thread safety:** All state access synchronized. Debug events arrive on Eclipse's debug event thread.

### BreakpointManager — ID-based breakpoint tracking

Wraps Eclipse's `IBreakpointManager`. Assigns auto-incrementing integer IDs to breakpoints (simpler for AI than Eclipse's `IMarker` handles).

- `setBreakpoint(class, line, condition)` → creates `JDIDebugModel.createLineBreakpoint()`, stores mapping ID→IBreakpoint
- `removeBreakpoint(id)` → finds and deletes
- `listBreakpoints()` → returns all with their state
- Breakpoints use fully qualified class name + line number (not workspace file paths)

### Tool implementations

Each tool is a separate class implementing `IMcpTool`, registered via `DebugStartupHook`:

```
registry.addTool(new SetBreakpointTool(breakpointManager));
registry.addTool(new RemoveBreakpointTool(breakpointManager));
registry.addTool(new ListBreakpointsTool(breakpointManager));
registry.addTool(new GetDebugStateTool(debugContext));
registry.addTool(new ListThreadsTool(debugContext));
registry.addTool(new GetStackTraceTool(debugContext));
registry.addTool(new InspectVariableTool(debugContext));
registry.addTool(new EvaluateExpressionTool(debugContext));
registry.addTool(new StepTool(debugContext));
registry.addTool(new ResumeTool(debugContext));
```

### Variable inspection (shallow, drill-down)

When inspecting a variable:
- **Primitives:** `{name: "x", type: "int", value: "42"}`
- **Strings:** `{name: "s", type: "String", value: "hello"}`
- **Objects:** `{name: "obj", type: "com.example.Foo", value: "Foo@1a2b", fields: ["bar", "baz", "count"]}`
- **Arrays/Collections:** `{name: "list", type: "ArrayList", value: "size=3", length: 3, elements: [{index: 0, type: "String", value: "a"}, ...]}`

To drill deeper, AI calls `inspect_variable(path="obj.bar")` — the `path` parameter navigates dot-separated field access.

Array elements accessed via `path="list[0]"` or `path="list[0].fieldName"`.

### Expression evaluation

Uses `IAstEvaluationEngine` from JDT Debug:
1. Get the `IJavaStackFrame` from debug context
2. Get the `IJavaDebugTarget`
3. Create an `IAstEvaluationEngine` for the target
4. `evaluateExpression(expression, frame, listener, ...)` — async, we wait for result
5. Return result in same shallow format as inspect_variable

Can execute arbitrary Java: check values, call methods, modify variables.

### Error handling

- Tools that require a suspended thread return a clear error if no debug session is active or no thread is suspended
- `step` waits for the step to complete (suspend event) with a timeout (default 30s)
- Invalid breakpoint locations return Eclipse's error message
- Expression evaluation errors return the compilation/runtime error message

## Dependencies (MANIFEST.MF additions)

```
org.eclipse.jdt.debug
org.eclipse.jdt.debug.ui
```

These provide `JDIDebugModel`, `IJavaDebugTarget`, `IJavaThread`, `IJavaStackFrame`, `IJavaVariable`, `IAstEvaluationEngine`.

## File Structure

```
eclipse.mcp.server.debugging/src/main/java/uk/l3si/eclipse/mcp/debugging/
├── DebugStartupHook.java          (modified — register tools + listener)
├── DebugContext.java               (new — debug state tracking)
├── BreakpointManager.java          (new — ID-based breakpoint management)
└── tools/
    ├── SetBreakpointTool.java
    ├── RemoveBreakpointTool.java
    ├── ListBreakpointsTool.java
    ├── GetDebugStateTool.java
    ├── ListThreadsTool.java
    ├── GetStackTraceTool.java
    ├── InspectVariableTool.java
    ├── EvaluateExpressionTool.java
    ├── StepTool.java
    └── ResumeTool.java
```
