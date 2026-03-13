# BPMN2 MCP Tools — Design Spec

## Overview

A new Eclipse plugin module (`eclipse.mcp.server.bpmn2`) providing 12 MCP tools for creating and editing BPMN2 process files. The tools allow an LLM to read, create, and modify BPMN2 files programmatically, including auto-layout for clean visual output in the Eclipse BPMN2 Modeler.

The module follows the same architecture as `eclipse.mcp.server.debugging` — a separate JAR with its own version, registered via `IStartup` extension point.

All tool names are prefixed with `bpmn2_` to avoid collisions with tools from other modules.

## Target BPMN2 Dialect

jBPM-flavored BPMN2 with JBoss/Drools extensions (`tns:` namespace). Supports:
- Process elements: startEvent, endEvent, task, scriptTask, userTask, callActivity, exclusiveGateway
- Sequence flows with Java condition expressions
- Process variables with itemDefinitions
- Signal definitions and signal start events
- Extension elements: `tns:metaData`, `tns:import`, `tns:taskName`, `tns:displayName`, `tns:icon`
- ioSpecification with dataInput/dataOutput associations
- BPMNDi diagram section (shapes, edges, labels, waypoints)

## Module Structure

```
eclipse.mcp.server.bpmn2/
├── META-INF/MANIFEST.MF
├── plugin.xml
├── pom.xml
├── src/main/java/uk/l3si/eclipse/mcp/bpmn2/
│   ├── Bpmn2StartupHook.java
│   ├── Bpmn2Document.java
│   ├── Bpmn2IdGenerator.java
│   ├── Bpmn2LayoutEngine.java
│   ├── model/
│   │   ├── ProcessInfo.java
│   │   ├── NodeInfo.java
│   │   ├── FlowInfo.java
│   │   ├── VariableInfo.java
│   │   ├── SignalInfo.java
│   │   └── LayoutResult.java
│   └── tools/
│       ├── GetProcessTool.java
│       ├── CreateProcessTool.java
│       ├── AddNodeTool.java
│       ├── UpdateNodeTool.java
│       ├── RemoveNodeTool.java
│       ├── AddFlowTool.java
│       ├── UpdateFlowTool.java
│       ├── RemoveFlowTool.java
│       ├── AddVariableTool.java
│       ├── RemoveVariableTool.java
│       ├── AddSignalTool.java
│       └── AutoLayoutTool.java
└── src/test/java/...
```

## Dependencies

MANIFEST.MF Require-Bundle:
- `eclipse.mcp.server` — McpTool, Args, InputSchema, ToolRegistry
- `org.eclipse.core.runtime`
- `org.eclipse.ui` — required for `IStartup` extension point
- `com.google.gson`

No BPMN2-specific Eclipse bundles. All XML manipulation uses `javax.xml` / `org.w3c.dom` (JDK built-in).

## Registration

`Bpmn2StartupHook` implements `IStartup`, registered via plugin.xml:

```xml
<extension point="org.eclipse.ui.startup">
   <startup class="uk.l3si.eclipse.mcp.bpmn2.Bpmn2StartupHook"/>
</extension>
```

On `earlyStartup()`, creates and registers all 12 tools via `ToolRegistry.addTool()`.

## Versioning

- Module version: `0.1.0-SNAPSHOT`
- Independent version from core (same pattern as debugging at `0.6.5`)
- Added as `<module>eclipse.mcp.server.bpmn2</module>` in parent pom.xml

---

## Bpmn2Document

Central class for XML manipulation, shared by all tools.

### Responsibilities

- Parse a `.bpmn2` file into a namespace-aware DOM `Document`
- Query methods: `findNodeById(id)`, `findFlowById(id)`, `getProcessElement()`, `listNodes()`, `listFlows()`, `listVariables()`, `listSignals()`
- Mutation methods: `addElement(parent, ns, tagName, attributes)`, `removeElement(node)`, `setAttribute(node, name, value)`
- Write back to file with consistent formatting (2-space indent, XML declaration)
- Handle namespace prefixes via `getElementsByTagNameNS()` and `createElementNS()` — never string-match on prefixes

### Namespace Constants

| Prefix | URI |
|--------|-----|
| `bpmn2` | `http://www.omg.org/spec/BPMN/20100524/MODEL` |
| `bpmndi` | `http://www.omg.org/spec/BPMN/20100524/DI` |
| `dc` | `http://www.omg.org/spec/DD/20100524/DC` |
| `di` | `http://www.omg.org/spec/DD/20100524/DI` |
| `tns` | `http://www.jboss.org/drools` |
| `xsi` | `http://www.w3.org/2001/XMLSchema-instance` |

### ID Generation (Bpmn2IdGenerator)

On parse, scans all `id` attributes to build a set of used IDs. Generates next available ID by type:
- `Task_N`, `ScriptTask_N`, `UserTask_N`, `CallActivity_N`
- `ExclusiveGateway_N`, `StartEvent_N`, `EndEvent_N`
- `SequenceFlow_N`
- `Signal_N`
- `DataInput_N`, `DataOutput_N`, `InputSet_N`, `OutputSet_N`
- `InputOutputSpecification_N`, `DataInputAssociation_N`, `DataOutputAssociation_N`
- `ItemDefinition_N`, `FormalExpression_N`

### Validation Helpers

- `requireNodeExists(id)` — throws `IllegalArgumentException` with "Node not found: '{id}'"
- `requireFlowExists(id)` — throws with "Sequence flow not found: '{id}'"
- `requireNodeType(id, expectedTypes...)` — throws with "Node '{id}' is a {actualType}, expected one of: {types}"

---

## Tool Specifications

### 1. bpmn2_get_process

**Purpose:** Parse a BPMN2 file and return a compact structured view.

**Parameters:**
| Name | Type | Required | Description |
|------|------|----------|-------------|
| `file` | string | yes | Absolute path to .bpmn2 file |

**Validation:**
- File exists and is readable
- Valid XML (parseable)
- Has `bpmn2:definitions` root element
- Has at least one `bpmn2:process` element

**Response (ProcessInfo):**
```json
{
  "processId": "com.example.my_flow",
  "processName": "my_flow",
  "packageName": "com.example",
  "variables": [{"name": "processCommandFlow", "type": "com.example.CommandFlow"}],
  "signals": [{"id": "Signal_1", "name": "com.example:beforeInput"}],
  "nodes": [
    {"id": "StartEvent_1", "type": "startEvent", "name": "StartProcess"},
    {"id": "Task_1", "type": "task", "name": "Handle Errors", "taskName": "com.example.IService_handle"},
    {"id": "ScriptTask_1", "type": "scriptTask", "name": "Get Property", "script": "..."},
    {"id": "CallActivity_1", "type": "callActivity", "name": "price_check_flow", "calledElement": "price_check_flow"},
    {"id": "ExclusiveGateway_1", "type": "exclusiveGateway", "name": "hasErrors", "direction": "diverging"}
  ],
  "flows": [
    {"id": "SequenceFlow_1", "source": "StartEvent_1", "target": "CallActivity_1"},
    {"id": "SequenceFlow_7", "source": "ExclusiveGateway_1", "target": "ExclusiveGateway_2", "name": "YES", "condition": "return ErrorRules.isError(pcf);"}
  ]
}
```

Strips BPMNDi diagram info, ioSpecification boilerplate, and extension element noise.

### 2. bpmn2_create_process

**Purpose:** Create a new .bpmn2 file with boilerplate XML.

**Parameters:**
| Name | Type | Required | Description |
|------|------|----------|-------------|
| `file` | string | yes | Absolute path for new file |
| `processId` | string | yes | Process identifier (e.g. `com.example.my_flow`) |
| `processName` | string | yes | Human-readable name |
| `packageName` | string | yes | Package name (e.g. `com.example`) |

**Validation:**
- File doesn't already exist
- `processId` non-blank, valid identifier (letters, digits, dots, underscores)
- `processName` non-blank
- `packageName` non-blank
- Parent directory exists

**Behavior:** Generates full XML with all namespace declarations, common itemDefinitions (String, Integer, Boolean), empty process with extensionElements for tns:import placeholder, and empty BPMNDiagram.

**Response:** `{"file": "/path/to/flow.bpmn2", "processId": "com.example.my_flow"}`

### 3. bpmn2_add_node

**Purpose:** Add a flow node to the process.

**Parameters:**
| Name | Type | Required | Description |
|------|------|----------|-------------|
| `file` | string | yes | Path to .bpmn2 file |
| `type` | string (enum) | yes | `startEvent`, `endEvent`, `task`, `scriptTask`, `userTask`, `callActivity`, `exclusiveGateway` |
| `name` | string | yes | Display name |
| `id` | string | no | Node ID (auto-generated if omitted) |
| `taskName` | string | conditional | Required for `task`. Fully qualified service interface + method |
| `displayName` | string | no | Display name for tasks (tns:displayName) |
| `icon` | string | no | Icon name for tasks (tns:icon) |
| `script` | string | conditional | Required for `scriptTask` |
| `scriptFormat` | string | no | Script language (default: `http://www.java.com/java`) |
| `calledElement` | string | conditional | Required for `callActivity` |
| `direction` | string (enum) | conditional | Required for `exclusiveGateway`: `diverging` or `converging` |
| `signalRef` | string | no | Signal ID for signal start events |
| `groupId` | string | no | Group ID for `userTask` |

**Validation:**
- File exists and is valid BPMN2
- `type` is one of the supported enum values
- `name` non-blank
- `id` not already taken (if provided)
- Type-specific required properties enforced:
  - `task` requires `taskName`
  - `scriptTask` requires `script`
  - `callActivity` requires `calledElement`
  - `exclusiveGateway` requires `direction` as `diverging` or `converging`
- `signalRef` if provided must reference an existing signal definition
- Only one plain startEvent per process (signal start events allowed multiple)

**Behavior:** Adds the process element with:
- `tns:metaData` extension element (elementname)
- ioSpecification boilerplate with dataInput/dataOutput for `taskCommandFlow` (for task, scriptTask, userTask, callActivity)
- dataInputAssociation/dataOutputAssociation mapped to process `processCommandFlow` variable
- For callActivity: uses `processCommandFlow` as both input and output variable names
- Does NOT add diagram shape — `bpmn2_auto_layout` handles that

**Response:** `{"id": "Task_5", "type": "task", "name": "My Task"}`

### 4. bpmn2_update_node

**Purpose:** Modify properties of an existing node.

**Parameters:**
| Name | Type | Required | Description |
|------|------|----------|-------------|
| `file` | string | yes | Path to .bpmn2 file |
| `id` | string | yes | Node ID to update |
| `name` | string | no | New display name |
| `script` | string | no | New script (scriptTask only) |
| `scriptFormat` | string | no | New script format (scriptTask only) |
| `calledElement` | string | no | New called element (callActivity only) |
| `taskName` | string | no | New task name (task/userTask only) |
| `displayName` | string | no | New display name (task only) |
| `direction` | string | no | New direction (exclusiveGateway only) |

**Validation:**
- Node exists
- Property is valid for the node type (e.g. can't set `script` on a callActivity)
- `calledElement` non-blank if provided
- If changing gateway `direction`, validate against actual flow count

**Response:** `{"id": "Task_1", "updated": ["name", "taskName"]}`

### 5. bpmn2_remove_node

**Purpose:** Remove a node and clean up connected flows.

**Parameters:**
| Name | Type | Required | Description |
|------|------|----------|-------------|
| `file` | string | yes | Path to .bpmn2 file |
| `id` | string | yes | Node ID to remove |

**Validation:**
- Node exists
- Not the only startEvent in the process

**Behavior:**
- Removes the node element from the process
- Removes all sequence flows where this node is source or target
- Cleans up `incoming`/`outgoing` refs on connected nodes
- Removes corresponding BPMNShape from diagram section
- Removes corresponding BPMNEdges from diagram section

**Response:** `{"id": "Task_1", "removedFlows": ["SequenceFlow_3", "SequenceFlow_4"]}`

### 6. bpmn2_add_flow

**Purpose:** Connect two nodes with a sequence flow.

**Parameters:**
| Name | Type | Required | Description |
|------|------|----------|-------------|
| `file` | string | yes | Path to .bpmn2 file |
| `source` | string | yes | Source node ID |
| `target` | string | yes | Target node ID |
| `name` | string | no | Label (e.g. "YES", "NO") |
| `condition` | string | no | Java condition expression |
| `priority` | integer | no | Flow priority (default: 1) |

**Validation:**
- Source node exists
- Target node exists
- Source != target (no self-loops)
- No duplicate flow between same source and target in same direction
- Source is not an endEvent
- Target is not a plain startEvent (signal start events are ok — they don't have incoming flows from within the process)
- If source is a diverging gateway and this isn't the first outgoing flow, warn if `condition` is missing (warn in response, not block)

**Behavior:**
- Creates `bpmn2:sequenceFlow` element with `sourceRef` and `targetRef`
- Adds `bpmn2:outgoing` ref to source node
- Adds `bpmn2:incoming` ref to target node
- If `condition` provided, adds `bpmn2:conditionExpression` child element
- Sets `tns:priority` attribute
- Does NOT add diagram edge — `bpmn2_auto_layout` handles that

**Response:** `{"id": "SequenceFlow_21", "source": "ExclusiveGateway_1", "target": "CallActivity_2"}`

### 7. bpmn2_update_flow

**Purpose:** Modify properties of an existing sequence flow.

**Parameters:**
| Name | Type | Required | Description |
|------|------|----------|-------------|
| `file` | string | yes | Path to .bpmn2 file |
| `id` | string | yes | Flow ID to update |
| `name` | string | no | New label |
| `condition` | string | no | New condition expression |
| `priority` | integer | no | New priority |

**Validation:**
- Flow exists
- If setting condition on a flow whose source isn't a gateway, include warning in response
- Condition non-blank if provided

**Response:** `{"id": "SequenceFlow_1", "updated": ["name", "condition"]}`

### 8. bpmn2_remove_flow

**Purpose:** Remove a sequence flow.

**Parameters:**
| Name | Type | Required | Description |
|------|------|----------|-------------|
| `file` | string | yes | Path to .bpmn2 file |
| `id` | string | yes | Flow ID to remove |

**Validation:**
- Flow exists

**Behavior:**
- Removes the sequence flow element
- Removes `incoming`/`outgoing` refs from connected nodes
- Removes corresponding BPMNEdge from diagram section

**Response:** `{"id": "SequenceFlow_3", "warnings": ["Node 'Task_2' has no incoming flows"]}`

### 9. bpmn2_add_variable

**Purpose:** Add a process-level variable with its type definition.

**Parameters:**
| Name | Type | Required | Description |
|------|------|----------|-------------|
| `file` | string | yes | Path to .bpmn2 file |
| `name` | string | yes | Variable name |
| `type` | string | yes | Fully qualified Java type |

**Validation:**
- Variable name doesn't already exist in the process
- Variable name is a valid Java identifier (letters, digits, underscores, starts with letter)
- Type non-blank
- If an itemDefinition with the same `structureRef` already exists, reuse it

**Behavior:**
- Creates `bpmn2:itemDefinition` in definitions (unless one with same structureRef exists)
- Creates `bpmn2:property` in the process element with `itemSubjectRef` referencing the itemDefinition

**Response:** `{"name": "myVar", "type": "java.lang.String", "itemDefinitionId": "ItemDefinition_5"}`

### 10. bpmn2_remove_variable

**Purpose:** Remove a process-level variable and its type definition.

**Parameters:**
| Name | Type | Required | Description |
|------|------|----------|-------------|
| `file` | string | yes | Path to .bpmn2 file |
| `name` | string | yes | Variable name to remove |

**Validation:**
- Variable exists in the process

**Behavior:**
- Removes `bpmn2:property` element with matching name from process
- Removes `bpmn2:itemDefinition` from definitions if no other variable references it

**Response:** `{"name": "myVar", "removed": true}`

### 11. bpmn2_add_signal

**Purpose:** Add a signal definition to the BPMN2 file.

**Parameters:**
| Name | Type | Required | Description |
|------|------|----------|-------------|
| `file` | string | yes | Path to .bpmn2 file |
| `name` | string | yes | Signal name (e.g. `com.example:beforeInput`) |
| `id` | string | no | Signal ID (auto-generated if omitted) |

**Validation:**
- Signal name non-blank
- No existing signal with the same name
- `id` not already taken (if provided)

**Behavior:**
- Creates `bpmn2:signal` element in definitions with `id` and `name` attributes

**Response:** `{"id": "Signal_3", "name": "com.example:beforeInput"}`

### 12. bpmn2_auto_layout

**Purpose:** Rewrite the BPMNDi section with a clean hierarchical layout.

**Parameters:**
| Name | Type | Required | Description |
|------|------|----------|-------------|
| `file` | string | yes | Path to .bpmn2 file |

**Validation:**
- File exists and is valid BPMN2
- Process has at least one node

**Behavior:** Runs the recursive block layout algorithm, clears the existing BPMNDi section, and writes new shapes, edges, labels, and waypoints.

**Response:** `{"file": "/path/to/flow.bpmn2", "nodesLaid": 12}`

---

## Auto-Layout Algorithm

### Block Decomposition

The process graph is decomposed into a tree of blocks:

**LinearBlock:** A sequence of nodes connected by single flows.
```
A → B → C
```
Laid out as a vertical stack in a single column.

**BranchBlock:** Starts at a diverging gateway, ends at its matching converging gateway. Contains N child blocks (one per branch).
```
     [diverge]
    /    |     \
  [b1]  [b2]  [b3]     ← each is a block (may contain nested BranchBlocks)
    \    |     /
     [converge]
```
Children laid out side by side horizontally.

### Finding Matching Converge Gateways

From a diverging gateway, walk all outgoing paths forward. Track which nodes each path visits. The first converging gateway reached by ALL paths is the matching converge.

Algorithm:
1. For each outgoing flow from the diverging gateway, do a BFS
2. Track the set of nodes visited per path
3. The matching converge is the first node that appears in ALL path sets and is a converging gateway
4. If no converge is found (non-structured graph), return `null` — handled as open branches

### Handling Non-Structured Graphs

Not all BPMN2 processes are well-structured (matched diverge/converge pairs). Common cases:

- **Branch ends at endEvent:** A diverging gateway where one or more branches terminate at an endEvent rather than converging. No matching converge gateway exists.
- **Early-exit branches:** A branch leads to an endEvent while others continue the main flow.

When `findMatchingConverge` returns `null`:
- The `BranchBlock` has `converge = null`
- Each branch is decomposed independently with `endNodeId = null` (walk to the end of each path)
- Layout: branches are placed side by side, but no converge gateway is placed at the bottom
- The block height is `gatewayHeight + GAP + max(height(child) for child in branches)`

### Decomposition Algorithm

```
function decompose(startNodeId, endNodeId):
    block = new LinearBlock
    current = startNodeId

    while current != null && current != endNodeId:
        if current is a diverging gateway:
            converge = findMatchingConverge(current)
            branches = []
            for each outgoing flow from current:
                branchStart = flow.target
                branchEnd = converge  // null if non-structured
                branchBlock = decompose(branchStart, branchEnd)
                branches.append(branchBlock)
            block.append(BranchBlock(diverge=current, converge=converge, branches=branches))
            if converge == null: break  // no converge → branches terminate independently
            current = converge
        else:
            block.append(current)
            outgoing = getOutgoingFlows(current)
            if outgoing is empty: break
            current = outgoing[0].target

    if current == endNodeId && endNodeId != null:
        block.append(current)

    return block
```

### Layout Constants

| Constant | Value |
|----------|-------|
| Task/CallActivity/ScriptTask/UserTask width | 110 |
| Task/CallActivity/ScriptTask/UserTask height | 50 |
| ExclusiveGateway width/height | 50 |
| StartEvent/EndEvent width/height | 36 |
| Vertical gap between nodes | 40 |
| Horizontal gap between branches | 60 |
| Minimum column width | 170 |
| Label offset from gateway | 5 |

### Width Calculation (recursive)

```
function width(block):
    if block is LinearBlock:
        return max(MINIMUM_COLUMN_WIDTH, max node width in block)
    if block is BranchBlock:
        return sum(width(child) for child in block.branches) + GAP * (len(branches) - 1)
```

### Height Calculation (recursive)

```
function height(block):
    if block is LinearBlock:
        return sum(nodeHeight(n) for n in block.nodes) + GAP * (len(nodes) - 1)
    if block is BranchBlock:
        // diverge gateway + gap + tallest branch
        h = gatewayHeight + GAP + max(height(child) for child in branches)
        if block.converge != null:
            // + gap + converge gateway
            h += GAP + gatewayHeight
        return h
```

Note: The converge gateway height is only added once, inside the BranchBlock. A LinearBlock containing a BranchBlock followed by more nodes adds the BranchBlock height (which includes the converge) plus subsequent node heights.

### Position Assignment

```
function layout(block, x, y):
    if block is LinearBlock:
        centerX = x + width(block) / 2
        currentY = y
        for node in block.nodes:
            place node at (centerX - nodeWidth/2, currentY)
            currentY += nodeHeight + GAP

    if block is BranchBlock:
        totalWidth = width(block)
        centerX = x + totalWidth / 2

        // Place diverging gateway centered
        place diverge at (centerX - gatewayWidth/2, y)

        // Layout branches side by side
        branchY = y + gatewayHeight + GAP
        branchX = x
        maxBranchHeight = 0
        for branch in block.branches:
            branchWidth = width(branch)
            layout(branch, branchX, branchY)
            maxBranchHeight = max(maxBranchHeight, height(branch))
            branchX += branchWidth + GAP

        // Place converging gateway centered (if structured)
        if block.converge != null:
            convergeY = branchY + maxBranchHeight + GAP
            place converge at (centerX - gatewayWidth/2, convergeY)
```

### Edge Routing

All edges use `di:waypoint` elements within `BPMNEdge`. Waypoint coordinates are absolute.

- **Linear connections (same column):** 2 waypoints — source bottom-center `(x + w/2, y + h)` to target top-center `(x + w/2, y)`.
- **Branch start (diverge → first branch node):** 3 waypoints — gateway bottom-center, then horizontal to branch column center at the same y, then down to first node top-center. If branch is directly below gateway (same column center), use 2 waypoints.
- **Branch end (last branch node → converge):** 3 waypoints — last node bottom-center, then horizontal to converge column center at converge y, then to converge top-center. If directly above (same column center), use 2 waypoints.
- **Non-structured branch end (branch ending at endEvent):** 2 waypoints — straight line from last node bottom-center to endEvent top-center (endEvent is already placed within the branch column).

### Label Placement

Condition labels ("YES", "NO") placed at the first waypoint after the gateway, offset by `LABEL_OFFSET` pixels.

### Signal Start Events

Signal start events are not part of the main flow. Placed in a separate column to the left of the main process, connected to their target (usually a converging gateway).

---

## Error Message Style

All validation errors follow the pattern established in the existing tools:
- Clear, specific message about what's wrong
- Include the offending value
- Suggest what to do instead

Examples:
- `"Node not found: 'Task_99'. Use 'bpmn2_get_process' to see available nodes."`
- `"Cannot set 'script' on a callActivity. 'script' is only valid for scriptTask nodes."`
- `"Duplicate flow: a sequence flow from 'ExclusiveGateway_1' to 'Task_2' already exists (SequenceFlow_5)."`
- `"Variable 'processCommandFlow' already exists in the process. Use a different name."`
- `"File already exists: '/path/to/flow.bpmn2'. Use 'bpmn2_get_process' to read it, or choose a different path."`

---

## Testing Strategy

Unit tests for each tool using JUnit 5 + Mockito where needed:

- **Bpmn2Document tests:** Parse sample .bpmn2 file, verify query methods return correct results. Test mutation + save round-trip.
- **Bpmn2IdGenerator tests:** Verify ID generation avoids collisions.
- **Tool validation tests:** Each tool's validation rules tested with invalid inputs, verify correct error messages.
- **Bpmn2LayoutEngine tests:** Test block decomposition on known graph structures. Verify layout produces valid coordinates (no overlaps, correct containment).
- **Integration tests:** Create process → add nodes → add flows → auto_layout → verify output is valid BPMN2 XML that can be parsed back.

Test resources: Copy the sample BPMN2 file into `src/test/resources/` for parsing/layout tests.
