package uk.l3si.eclipse.mcp.bpmn2;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.*;

/**
 * Recursive block decomposition layout engine for BPMN2 process diagrams.
 * Decomposes the process graph into a tree of blocks (linear sequences and
 * gateway branches), computes positions for all nodes, and writes BPMNDi
 * shapes, edges, and labels to the document.
 */
public class Bpmn2LayoutEngine {

    // ---- Layout constants ----

    static final double TASK_WIDTH = 110;
    static final double TASK_HEIGHT = 50;
    static final double GATEWAY_SIZE = 50;
    static final double EVENT_SIZE = 36;
    static final double V_GAP = 40;
    static final double H_GAP = 60;
    static final double MIN_COLUMN_WIDTH = 170;
    static final double LABEL_OFFSET = 5;
    static final double START_X = 50;
    static final double START_Y = 50;

    // ---- Block model ----

    interface Block {
        double width();
        double height();
        void layout(double x, double y, Map<String, double[]> positions);
    }

    static class NodeBlock implements Block {
        final String nodeId;
        final String nodeType;

        NodeBlock(String nodeId, String nodeType) {
            this.nodeId = nodeId;
            this.nodeType = nodeType;
        }

        @Override
        public double width() {
            return nodeWidth(nodeType);
        }

        @Override
        public double height() {
            return nodeHeight(nodeType);
        }

        @Override
        public void layout(double x, double y, Map<String, double[]> positions) {
            // Position is top-left corner, centered within the column
            // The caller centers the node horizontally
            positions.put(nodeId, new double[]{x, y});
        }
    }

    static class LinearBlock implements Block {
        final List<Block> children = new ArrayList<>();

        void add(Block block) {
            children.add(block);
        }

        @Override
        public double width() {
            double maxChildWidth = 0;
            for (Block child : children) {
                maxChildWidth = Math.max(maxChildWidth, child.width());
            }
            return Math.max(MIN_COLUMN_WIDTH, maxChildWidth);
        }

        @Override
        public double height() {
            if (children.isEmpty()) return 0;
            double total = 0;
            for (Block child : children) {
                total += child.height();
            }
            total += V_GAP * (children.size() - 1);
            return total;
        }

        @Override
        public void layout(double x, double y, Map<String, double[]> positions) {
            double columnWidth = width();
            double centerX = x + columnWidth / 2.0;
            double currentY = y;
            for (Block child : children) {
                if (child instanceof NodeBlock nb) {
                    double nodeW = nb.width();
                    nb.layout(centerX - nodeW / 2.0, currentY, positions);
                    currentY += nb.height() + V_GAP;
                } else if (child instanceof BranchBlock bb) {
                    // BranchBlock centers itself within its own width
                    double bbWidth = bb.width();
                    double bbX = centerX - bbWidth / 2.0;
                    bb.layout(bbX, currentY, positions);
                    currentY += bb.height() + V_GAP;
                } else {
                    // LinearBlock nested (shouldn't normally happen but handle it)
                    double childWidth = child.width();
                    child.layout(centerX - childWidth / 2.0, currentY, positions);
                    currentY += child.height() + V_GAP;
                }
            }
        }
    }

    static class BranchBlock implements Block {
        final String divergeId;
        final String convergeId; // null for non-structured
        final List<Block> branches;

        BranchBlock(String divergeId, String convergeId, List<Block> branches) {
            this.divergeId = divergeId;
            this.convergeId = convergeId;
            this.branches = branches;
        }

        @Override
        public double width() {
            double total = 0;
            for (Block branch : branches) {
                total += branch.width();
            }
            if (branches.size() > 1) {
                total += H_GAP * (branches.size() - 1);
            }
            return Math.max(MIN_COLUMN_WIDTH, total);
        }

        @Override
        public double height() {
            double maxBranchHeight = 0;
            for (Block branch : branches) {
                maxBranchHeight = Math.max(maxBranchHeight, branch.height());
            }
            // diverge gateway + gap + tallest branch
            double h = GATEWAY_SIZE + V_GAP + maxBranchHeight;
            if (convergeId != null) {
                // + gap + converge gateway
                h += V_GAP + GATEWAY_SIZE;
            }
            return h;
        }

        @Override
        public void layout(double x, double y, Map<String, double[]> positions) {
            double totalWidth = width();
            double centerX = x + totalWidth / 2.0;

            // Place diverging gateway centered
            positions.put(divergeId, new double[]{
                    centerX - GATEWAY_SIZE / 2.0, y
            });

            // Layout branches side by side
            double branchY = y + GATEWAY_SIZE + V_GAP;

            // Calculate total branches width (sum of widths + gaps)
            double branchesWidth = 0;
            for (Block branch : branches) {
                branchesWidth += branch.width();
            }
            if (branches.size() > 1) {
                branchesWidth += H_GAP * (branches.size() - 1);
            }

            // Center the branches group within totalWidth
            double branchX = x + (totalWidth - branchesWidth) / 2.0;

            double maxBranchHeight = 0;
            for (Block branch : branches) {
                double branchWidth = branch.width();
                branch.layout(branchX, branchY, positions);
                maxBranchHeight = Math.max(maxBranchHeight, branch.height());
                branchX += branchWidth + H_GAP;
            }

            // Place converging gateway centered (if structured)
            if (convergeId != null) {
                double convergeY = branchY + maxBranchHeight + V_GAP;
                positions.put(convergeId, new double[]{
                        centerX - GATEWAY_SIZE / 2.0, convergeY
                });
            }
        }
    }

    // ---- Main layout method ----

    /**
     * Lays out all nodes in the process, clears the existing diagram,
     * and writes new shapes, edges, and labels.
     *
     * @param doc the BPMN2 document to lay out
     * @return the number of nodes laid out
     */
    public int layout(Bpmn2Document doc) {
        List<Element> nodes = doc.listNodes();
        if (nodes.isEmpty()) {
            return 0;
        }

        // Find the main start event (startEvent without signalEventDefinition)
        Element mainStart = null;
        List<Element> signalStarts = new ArrayList<>();
        for (Element node : nodes) {
            if ("startEvent".equals(node.getLocalName())) {
                if (hasSignalEventDefinition(node)) {
                    signalStarts.add(node);
                } else if (mainStart == null) {
                    mainStart = node;
                }
            }
        }

        Map<String, double[]> positions = new LinkedHashMap<>();

        if (mainStart != null) {
            // Build the block tree by decomposing from the start event
            String startId = mainStart.getAttribute("id");
            Block rootBlock = decompose(startId, null, doc);

            // Compute positions for all main-flow nodes
            rootBlock.layout(START_X, START_Y, positions);
        } else if (!signalStarts.isEmpty()) {
            // No main start, but signal starts exist - just place them
        } else {
            // No start events at all - place nodes linearly
            LinearBlock fallback = new LinearBlock();
            for (Element node : nodes) {
                fallback.add(new NodeBlock(node.getAttribute("id"), node.getLocalName()));
            }
            fallback.layout(START_X, START_Y, positions);
        }

        // Handle signal start events - place in a separate column to the left
        if (!signalStarts.isEmpty()) {
            double signalX = START_X - MIN_COLUMN_WIDTH;
            if (signalX < 10) signalX = 10;
            double signalY = START_Y;
            for (Element signalStart : signalStarts) {
                String id = signalStart.getAttribute("id");
                positions.put(id, new double[]{
                        signalX + (MIN_COLUMN_WIDTH - EVENT_SIZE) / 2.0, signalY
                });
                signalY += EVENT_SIZE + V_GAP;
            }
        }

        // Clear existing diagram and write new shapes
        doc.clearDiagram();

        // Add BPMNShapes for all positioned nodes
        for (Map.Entry<String, double[]> entry : positions.entrySet()) {
            String nodeId = entry.getKey();
            double[] pos = entry.getValue();
            Element node = doc.findNodeById(nodeId);
            if (node == null) continue;
            String type = node.getLocalName();
            double w = nodeWidth(type);
            double h = nodeHeight(type);
            doc.addShape(nodeId, pos[0], pos[1], w, h, type);
        }

        // Add BPMNEdges with waypoints for all flows
        for (Element flow : doc.listFlows()) {
            String flowId = flow.getAttribute("id");
            String sourceId = flow.getAttribute("sourceRef");
            String targetId = flow.getAttribute("targetRef");
            String flowName = flow.getAttribute("name");

            double[] sourcePos = positions.get(sourceId);
            double[] targetPos = positions.get(targetId);

            if (sourcePos == null || targetPos == null) {
                continue; // Skip flows involving unpositioned nodes
            }

            Element sourceNode = doc.findNodeById(sourceId);
            Element targetNode = doc.findNodeById(targetId);
            if (sourceNode == null || targetNode == null) continue;

            String sourceType = sourceNode.getLocalName();
            String targetType = targetNode.getLocalName();

            double sourceW = nodeWidth(sourceType);
            double sourceH = nodeHeight(sourceType);
            double targetW = nodeWidth(targetType);
            double targetH = nodeHeight(targetType);

            // Source bottom-center
            double srcCX = sourcePos[0] + sourceW / 2.0;
            double srcBY = sourcePos[1] + sourceH;
            // Target top-center
            double tgtCX = targetPos[0] + targetW / 2.0;
            double tgtTY = targetPos[1];

            List<double[]> waypoints = new ArrayList<>();

            if (Math.abs(srcCX - tgtCX) < 1.0) {
                // Same column center - 2 waypoints
                waypoints.add(new double[]{srcCX, srcBY});
                waypoints.add(new double[]{tgtCX, tgtTY});
            } else {
                // Different columns - 3 waypoints with bend
                waypoints.add(new double[]{srcCX, srcBY});
                // Determine bend Y
                double bendY;
                if (srcBY < tgtTY) {
                    // Diverge to branch: bend at source bottom + V_GAP/2
                    // or branch to converge: bend at target top - V_GAP/2
                    if ("exclusiveGateway".equals(sourceType)) {
                        bendY = srcBY + V_GAP / 2.0;
                    } else {
                        bendY = tgtTY - V_GAP / 2.0;
                    }
                } else {
                    bendY = (srcBY + tgtTY) / 2.0;
                }
                waypoints.add(new double[]{tgtCX, bendY});
                waypoints.add(new double[]{tgtCX, tgtTY});
            }

            doc.addEdge(flowId, waypoints, sourceId, targetId);

            // Label placement for named flows
            if (flowName != null && !flowName.isEmpty()) {
                double labelX = waypoints.get(0)[0] + LABEL_OFFSET;
                double labelY = waypoints.get(0)[1] + LABEL_OFFSET;
                doc.addLabel("BPMNEdge_" + flowId, labelX, labelY, flowName);
            }
        }

        return positions.size();
    }

    // ---- Decomposition algorithm ----

    Block decompose(String startNodeId, String endNodeId, Bpmn2Document doc) {
        LinearBlock block = new LinearBlock();
        String current = startNodeId;

        while (current != null && !current.equals(endNodeId)) {
            Element node = doc.findNodeById(current);
            if (node == null) break;

            String nodeType = node.getLocalName();

            if ("exclusiveGateway".equals(nodeType) && isDiverging(node)) {
                String converge = findMatchingConverge(current, doc);
                List<Block> branches = new ArrayList<>();
                List<String> outgoingTargets = getOutgoingTargets(current, doc);
                for (String branchTarget : outgoingTargets) {
                    Block branchBlock = decompose(branchTarget, converge, doc);
                    branches.add(branchBlock);
                }
                block.add(new BranchBlock(current, converge, branches));
                if (converge == null) {
                    break; // non-structured - branches terminate independently
                }
                // Advance past the converge gateway to the next node
                List<String> convergeOutgoing = getOutgoingTargets(converge, doc);
                if (convergeOutgoing.isEmpty()) {
                    current = null;
                } else {
                    current = convergeOutgoing.get(0);
                }
            } else {
                block.add(new NodeBlock(current, nodeType));
                List<String> outgoing = getOutgoingTargets(current, doc);
                if (outgoing.isEmpty()) {
                    break;
                }
                current = outgoing.get(0);
            }
        }

        return block;
    }

    // ---- Finding matching converge gateway ----

    String findMatchingConverge(String divergeId, Bpmn2Document doc) {
        List<String> outgoingTargets = getOutgoingTargets(divergeId, doc);
        if (outgoingTargets.isEmpty()) {
            return null;
        }

        // BFS from each outgoing path
        // Track sets of visited nodes per path
        List<Set<String>> pathSets = new ArrayList<>();
        List<Queue<String>> queues = new ArrayList<>();

        for (String target : outgoingTargets) {
            Set<String> visited = new LinkedHashSet<>();
            Queue<String> queue = new LinkedList<>();
            visited.add(target);
            queue.add(target);
            pathSets.add(visited);
            queues.add(queue);
        }

        // BFS level by level across all paths simultaneously
        // Look for the first converging gateway that appears in ALL path sets
        while (true) {
            boolean anyProgress = false;

            for (int i = 0; i < queues.size(); i++) {
                Queue<String> queue = queues.get(i);
                Set<String> visited = pathSets.get(i);

                if (queue.isEmpty()) continue;

                // Process one level
                int levelSize = queue.size();
                for (int j = 0; j < levelSize; j++) {
                    String nodeId = queue.poll();
                    anyProgress = true;

                    // Check if this is a converging gateway
                    Element node = doc.findNodeById(nodeId);
                    if (node != null && "exclusiveGateway".equals(node.getLocalName())
                            && isConverging(node)) {
                        // Check if it appears in all other path sets
                        boolean inAll = true;
                        for (int k = 0; k < pathSets.size(); k++) {
                            if (k != i && !pathSets.get(k).contains(nodeId)) {
                                inAll = false;
                                break;
                            }
                        }
                        if (inAll) {
                            return nodeId;
                        }
                    }

                    // Expand BFS
                    List<String> nextTargets = getOutgoingTargets(nodeId, doc);
                    for (String next : nextTargets) {
                        if (visited.add(next)) {
                            queue.add(next);
                        }
                    }
                }
            }

            if (!anyProgress) {
                break; // All queues exhausted
            }

            // After expanding, check if any converging gateway now appears in all sets
            for (Set<String> visited : pathSets) {
                for (String nodeId : visited) {
                    Element node = doc.findNodeById(nodeId);
                    if (node != null && "exclusiveGateway".equals(node.getLocalName())
                            && isConverging(node)) {
                        boolean inAll = true;
                        for (Set<String> other : pathSets) {
                            if (!other.contains(nodeId)) {
                                inAll = false;
                                break;
                            }
                        }
                        if (inAll) {
                            return nodeId;
                        }
                    }
                }
            }
        }

        return null; // Non-structured graph
    }

    // ---- Helper methods ----

    /**
     * Gets the target node IDs of all outgoing flows from a node.
     */
    List<String> getOutgoingTargets(String nodeId, Bpmn2Document doc) {
        List<String> targets = new ArrayList<>();
        for (Element flow : doc.listFlows()) {
            if (nodeId.equals(flow.getAttribute("sourceRef"))) {
                targets.add(flow.getAttribute("targetRef"));
            }
        }
        return targets;
    }

    private boolean isDiverging(Element gateway) {
        String direction = gateway.getAttribute("gatewayDirection");
        return "Diverging".equals(direction);
    }

    private boolean isConverging(Element gateway) {
        String direction = gateway.getAttribute("gatewayDirection");
        return "Converging".equals(direction);
    }

    private boolean hasSignalEventDefinition(Element startEvent) {
        NodeList children = startEvent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element el
                    && Bpmn2Document.NS_BPMN2.equals(el.getNamespaceURI())
                    && "signalEventDefinition".equals(el.getLocalName())) {
                return true;
            }
        }
        return false;
    }

    static double nodeWidth(String nodeType) {
        return switch (nodeType) {
            case "task", "scriptTask", "userTask", "callActivity" -> TASK_WIDTH;
            case "exclusiveGateway" -> GATEWAY_SIZE;
            case "startEvent", "endEvent" -> EVENT_SIZE;
            default -> TASK_WIDTH;
        };
    }

    static double nodeHeight(String nodeType) {
        return switch (nodeType) {
            case "task", "scriptTask", "userTask", "callActivity" -> TASK_HEIGHT;
            case "exclusiveGateway" -> GATEWAY_SIZE;
            case "startEvent", "endEvent" -> EVENT_SIZE;
            default -> TASK_HEIGHT;
        };
    }
}
