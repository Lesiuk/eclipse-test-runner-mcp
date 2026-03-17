package uk.l3si.eclipse.mcp.debugging;

import uk.l3si.eclipse.mcp.debugging.model.VariableResult;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.model.IVariable;
import org.eclipse.jdt.debug.core.IJavaArray;
import org.eclipse.jdt.debug.core.IJavaObject;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaThread;
import org.eclipse.jdt.debug.core.IJavaValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class VariableCollector {

    private static final int MAX_ARRAY_PREVIEW = 5;

    private static final Set<String> WELL_KNOWN_TYPES = Set.of(
            "java.lang.String", "java.lang.Integer", "java.lang.Long",
            "java.lang.Double", "java.lang.Float", "java.lang.Boolean",
            "java.lang.Byte", "java.lang.Short", "java.lang.Character",
            "java.lang.Number", "java.math.BigDecimal", "java.math.BigInteger",
            "java.lang.StringBuilder", "java.lang.StringBuffer",
            "java.util.Date", "java.time.LocalDate", "java.time.LocalDateTime",
            "java.time.Instant", "java.time.ZonedDateTime",
            "java.util.UUID", "java.net.URI", "java.net.URL",
            "java.io.File", "java.nio.file.Path");

    private final DebugContext debugContext;

    public VariableCollector(DebugContext debugContext) {
        this.debugContext = debugContext;
    }

    /**
     * Collect variables for the current top frame of the current suspended thread.
     * Returns null if no frame is available or on any error.
     */
    public static List<VariableResult> collectForCurrentFrame(DebugContext debugContext) {
        try {
            IJavaStackFrame frame = debugContext.resolveFrame(debugContext.resolveThread(null), null);
            return collect(frame, debugContext);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Collect variables from the given stack frame.
     * Returns null if the frame is no longer valid.
     */
    public static List<VariableResult> collect(IJavaStackFrame frame, DebugContext debugContext) {
        try {
            List<VariableResult> variables = new ArrayList<>();
            VariableCollector collector = new VariableCollector(debugContext);
            for (IVariable v : frame.getVariables()) {
                try {
                    if (v.getValue() instanceof IJavaValue javaValue) {
                        variables.add(collector.formatValue(v.getName(), javaValue));
                    }
                } catch (DebugException e) {
                    variables.add(VariableResult.builder()
                            .name(v.getName())
                            .type("unknown")
                            .value("<error: " + e.getMessage() + ">")
                            .build());
                }
            }
            return variables;
        } catch (Exception e) {
            return null;
        }
    }

    public VariableResult formatValue(String name, IJavaValue value) throws DebugException {
        VariableResult.VariableResultBuilder builder = VariableResult.builder()
                .name(name)
                .type(value.getReferenceTypeName());

        if (value.isNull()) {
            builder.value("null");
        } else if (value instanceof IJavaArray array) {
            int length = array.getLength();
            builder.length(length);

            int preview = Math.min(length, MAX_ARRAY_PREVIEW);
            List<String> items = new ArrayList<>();
            for (int i = 0; i < preview; i++) {
                IJavaValue elem = array.getValue(i);
                items.add(elem.isNull() ? "null" : elem.getValueString());
            }
            builder.value(items);
            if (length > preview) {
                builder.truncated(true);
            }
        } else if (value instanceof IJavaObject obj) {
            String typeName = obj.getReferenceTypeName();
            if (isWellKnownType(typeName)
                    || isCollectionType(typeName)
                    || isMapType(typeName)) {
                String display = safeToString(obj);
                builder.value(display != null ? display : obj.getValueString());
            } else {
                builder.value(obj.getValueString());
                List<String> fieldNames = new ArrayList<>();
                for (IVariable v : obj.getVariables()) {
                    fieldNames.add(v.getName());
                }
                if (!fieldNames.isEmpty()) {
                    builder.fields(fieldNames);
                }
            }
        } else {
            builder.value(value.getValueString());
        }

        return builder.build();
    }

    private String safeToString(IJavaObject obj) {
        try {
            IJavaThread thread = debugContext.resolveThread(null);
            IJavaValue result = obj.sendMessage("toString",
                    "()Ljava/lang/String;", new IJavaValue[0], thread, false);
            if (result != null) {
                String s = result.getValueString();
                return s.length() > 200 ? s.substring(0, 197) + "..." : s;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public static boolean isWellKnownType(String typeName) {
        return typeName != null && (WELL_KNOWN_TYPES.contains(typeName)
                || typeName.startsWith("java.lang.") && typeName.indexOf('.', 10) == -1);
    }

    public static boolean isCollectionType(String typeName) {
        if (typeName == null) return false;
        if (typeName.startsWith("java.util.")) {
            String simple = typeName.substring(10);
            return simple.contains("List") || simple.contains("Set")
                    || simple.contains("Queue") || simple.contains("Deque")
                    || simple.contains("Stack") || simple.contains("Vector");
        }
        return false;
    }

    public static boolean isMapType(String typeName) {
        return typeName != null && typeName.startsWith("java.util.")
                && typeName.substring(10).contains("Map");
    }
}
