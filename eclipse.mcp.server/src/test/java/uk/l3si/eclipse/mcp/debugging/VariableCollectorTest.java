package uk.l3si.eclipse.mcp.debugging;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.model.IVariable;
import org.eclipse.jdt.debug.core.IJavaArray;
import org.eclipse.jdt.debug.core.IJavaObject;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaThread;
import org.eclipse.jdt.debug.core.IJavaValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import uk.l3si.eclipse.mcp.debugging.model.VariableResult;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VariableCollectorTest {

    private static final Gson GSON = new Gson();

    private DebugContext debugContext;

    @BeforeEach
    void setUp() {
        debugContext = mock(DebugContext.class);
    }

    // ---- helpers ----

    private IVariable mockVariable(String name, IJavaValue value) throws DebugException {
        IVariable v = mock(IVariable.class);
        when(v.getName()).thenReturn(name);
        when(v.getValue()).thenReturn(value);
        return v;
    }

    private IJavaValue mockPrimitive(String typeName, String valueString) throws DebugException {
        IJavaValue value = mock(IJavaValue.class);
        when(value.getReferenceTypeName()).thenReturn(typeName);
        when(value.getValueString()).thenReturn(valueString);
        when(value.isNull()).thenReturn(false);
        return value;
    }

    private IJavaValue mockNullValue(String typeName) throws DebugException {
        IJavaValue value = mock(IJavaValue.class);
        when(value.getReferenceTypeName()).thenReturn(typeName);
        when(value.isNull()).thenReturn(true);
        return value;
    }

    private IJavaObject mockObject(String typeName, String valueString, String... fieldNames) throws DebugException {
        IJavaObject obj = mock(IJavaObject.class);
        when(obj.getReferenceTypeName()).thenReturn(typeName);
        when(obj.getValueString()).thenReturn(valueString);
        when(obj.isNull()).thenReturn(false);
        IVariable[] fields = new IVariable[fieldNames.length];
        for (int i = 0; i < fieldNames.length; i++) {
            fields[i] = mock(IVariable.class);
            when(fields[i].getName()).thenReturn(fieldNames[i]);
        }
        when(obj.getVariables()).thenReturn(fields);
        return obj;
    }

    private IJavaObject mockWellKnownObject(String typeName, String toStringResult) throws Exception {
        IJavaObject obj = mock(IJavaObject.class);
        when(obj.getReferenceTypeName()).thenReturn(typeName);
        when(obj.getValueString()).thenReturn("raw-" + toStringResult);
        when(obj.isNull()).thenReturn(false);

        IJavaThread thread = mock(IJavaThread.class);
        when(debugContext.resolveThread(null)).thenReturn(thread);

        IJavaValue toStringValue = mock(IJavaValue.class);
        when(toStringValue.getValueString()).thenReturn(toStringResult);
        when(obj.sendMessage(eq("toString"), eq("()Ljava/lang/String;"), any(), eq(thread), eq(false)))
                .thenReturn(toStringValue);
        return obj;
    }

    private IJavaArray mockArray(String typeName, int totalLength, String... previewValues) throws DebugException {
        IJavaArray array = mock(IJavaArray.class);
        when(array.getReferenceTypeName()).thenReturn(typeName);
        when(array.isNull()).thenReturn(false);
        when(array.getLength()).thenReturn(totalLength);
        for (int i = 0; i < previewValues.length; i++) {
            IJavaValue elem = mock(IJavaValue.class);
            if ("null".equals(previewValues[i])) {
                when(elem.isNull()).thenReturn(true);
            } else {
                when(elem.isNull()).thenReturn(false);
                when(elem.getValueString()).thenReturn(previewValues[i]);
            }
            when(array.getValue(i)).thenReturn(elem);
        }
        return array;
    }

    private IJavaStackFrame mockFrameWithVariables(IVariable... vars) throws DebugException {
        IJavaStackFrame frame = mock(IJavaStackFrame.class);
        when(frame.getVariables()).thenReturn(vars);
        return frame;
    }

    private JsonObject toJson(VariableResult result) {
        return GSON.toJsonTree(result).getAsJsonObject();
    }

    // ---- type classification tests ----

    @Nested
    class IsWellKnownType {
        @Test
        void recognizesBoxedPrimitives() {
            assertTrue(VariableCollector.isWellKnownType("java.lang.String"));
            assertTrue(VariableCollector.isWellKnownType("java.lang.Integer"));
            assertTrue(VariableCollector.isWellKnownType("java.lang.Long"));
            assertTrue(VariableCollector.isWellKnownType("java.lang.Double"));
            assertTrue(VariableCollector.isWellKnownType("java.lang.Float"));
            assertTrue(VariableCollector.isWellKnownType("java.lang.Boolean"));
            assertTrue(VariableCollector.isWellKnownType("java.lang.Byte"));
            assertTrue(VariableCollector.isWellKnownType("java.lang.Short"));
            assertTrue(VariableCollector.isWellKnownType("java.lang.Character"));
        }

        @Test
        void recognizesMathTypes() {
            assertTrue(VariableCollector.isWellKnownType("java.math.BigDecimal"));
            assertTrue(VariableCollector.isWellKnownType("java.math.BigInteger"));
        }

        @Test
        void recognizesDateTimeTypes() {
            assertTrue(VariableCollector.isWellKnownType("java.time.LocalDate"));
            assertTrue(VariableCollector.isWellKnownType("java.time.LocalDateTime"));
            assertTrue(VariableCollector.isWellKnownType("java.time.Instant"));
            assertTrue(VariableCollector.isWellKnownType("java.time.ZonedDateTime"));
            assertTrue(VariableCollector.isWellKnownType("java.util.Date"));
        }

        @Test
        void recognizesUtilityTypes() {
            assertTrue(VariableCollector.isWellKnownType("java.util.UUID"));
            assertTrue(VariableCollector.isWellKnownType("java.net.URI"));
            assertTrue(VariableCollector.isWellKnownType("java.net.URL"));
            assertTrue(VariableCollector.isWellKnownType("java.io.File"));
            assertTrue(VariableCollector.isWellKnownType("java.nio.file.Path"));
        }

        @Test
        void recognizesStringBuilderTypes() {
            assertTrue(VariableCollector.isWellKnownType("java.lang.StringBuilder"));
            assertTrue(VariableCollector.isWellKnownType("java.lang.StringBuffer"));
            assertTrue(VariableCollector.isWellKnownType("java.lang.Number"));
        }

        @Test
        void matchesAnyTopLevelJavaLangClass() {
            // Any simple class under java.lang.* (not sub-packages) is well-known
            assertTrue(VariableCollector.isWellKnownType("java.lang.Void"));
            assertTrue(VariableCollector.isWellKnownType("java.lang.Thread"));
        }

        @Test
        void rejectsJavaLangSubPackages() {
            assertFalse(VariableCollector.isWellKnownType("java.lang.reflect.Method"));
            assertFalse(VariableCollector.isWellKnownType("java.lang.invoke.MethodHandle"));
        }

        @Test
        void rejectsCollectionsAndMaps() {
            assertFalse(VariableCollector.isWellKnownType("java.util.ArrayList"));
            assertFalse(VariableCollector.isWellKnownType("java.util.HashMap"));
        }

        @Test
        void rejectsCustomClasses() {
            assertFalse(VariableCollector.isWellKnownType("com.example.MyClass"));
            assertFalse(VariableCollector.isWellKnownType("org.foo.Bar"));
        }

        @Test
        void rejectsNull() {
            assertFalse(VariableCollector.isWellKnownType(null));
        }
    }

    @Nested
    class IsCollectionType {
        @Test
        void recognizesListTypes() {
            assertTrue(VariableCollector.isCollectionType("java.util.ArrayList"));
            assertTrue(VariableCollector.isCollectionType("java.util.LinkedList"));
            assertTrue(VariableCollector.isCollectionType("java.util.Collections$UnmodifiableList"));
        }

        @Test
        void recognizesSetTypes() {
            assertTrue(VariableCollector.isCollectionType("java.util.HashSet"));
            assertTrue(VariableCollector.isCollectionType("java.util.TreeSet"));
            assertTrue(VariableCollector.isCollectionType("java.util.LinkedHashSet"));
        }

        @Test
        void recognizesQueueAndDequeTypes() {
            assertTrue(VariableCollector.isCollectionType("java.util.ArrayDeque"));
            assertTrue(VariableCollector.isCollectionType("java.util.PriorityQueue"));
        }

        @Test
        void recognizesStackAndVector() {
            assertTrue(VariableCollector.isCollectionType("java.util.Stack"));
            assertTrue(VariableCollector.isCollectionType("java.util.Vector"));
        }

        @Test
        void rejectsMaps() {
            assertFalse(VariableCollector.isCollectionType("java.util.HashMap"));
            assertFalse(VariableCollector.isCollectionType("java.util.TreeMap"));
        }

        @Test
        void rejectsNonJavaUtil() {
            assertFalse(VariableCollector.isCollectionType("com.example.MyList"));
            assertFalse(VariableCollector.isCollectionType("java.lang.String"));
        }

        @Test
        void rejectsNull() {
            assertFalse(VariableCollector.isCollectionType(null));
        }
    }

    @Nested
    class IsMapType {
        @Test
        void recognizesMapTypes() {
            assertTrue(VariableCollector.isMapType("java.util.HashMap"));
            assertTrue(VariableCollector.isMapType("java.util.TreeMap"));
            assertTrue(VariableCollector.isMapType("java.util.LinkedHashMap"));
            assertTrue(VariableCollector.isMapType("java.util.concurrent.ConcurrentHashMap"));
        }

        @Test
        void rejectsCollections() {
            assertFalse(VariableCollector.isMapType("java.util.ArrayList"));
            assertFalse(VariableCollector.isMapType("java.util.HashSet"));
        }

        @Test
        void rejectsNonJavaUtil() {
            assertFalse(VariableCollector.isMapType("com.example.MyMap"));
            assertFalse(VariableCollector.isMapType("java.lang.String"));
        }

        @Test
        void rejectsNull() {
            assertFalse(VariableCollector.isMapType(null));
        }
    }

    // ---- formatValue tests ----

    @Nested
    class FormatValue {
        private VariableCollector collector;

        @BeforeEach
        void setUp() {
            collector = new VariableCollector(debugContext);
        }

        @Test
        void primitiveInt() throws Exception {
            IJavaValue value = mockPrimitive("int", "42");
            JsonObject json = toJson(collector.formatValue("count", value));
            assertEquals("count", json.get("name").getAsString());
            assertEquals("int", json.get("type").getAsString());
            assertEquals("42", json.get("value").getAsString());
        }

        @Test
        void primitiveBoolean() throws Exception {
            IJavaValue value = mockPrimitive("boolean", "true");
            JsonObject json = toJson(collector.formatValue("flag", value));
            assertEquals("boolean", json.get("type").getAsString());
            assertEquals("true", json.get("value").getAsString());
        }

        @Test
        void primitiveDouble() throws Exception {
            IJavaValue value = mockPrimitive("double", "3.14");
            JsonObject json = toJson(collector.formatValue("pi", value));
            assertEquals("3.14", json.get("value").getAsString());
        }

        @Test
        void nullValue() throws Exception {
            IJavaValue value = mockNullValue("java.lang.String");
            JsonObject json = toJson(collector.formatValue("name", value));
            assertEquals("name", json.get("name").getAsString());
            assertEquals("java.lang.String", json.get("type").getAsString());
            assertEquals("null", json.get("value").getAsString());
        }

        @Test
        void smallArray() throws Exception {
            IJavaArray array = mockArray("int[]", 3, "1", "2", "3");
            JsonObject json = toJson(collector.formatValue("nums", array));
            assertEquals("nums", json.get("name").getAsString());
            assertEquals("int[]", json.get("type").getAsString());
            assertEquals(3, json.get("length").getAsInt());
            JsonArray items = json.get("value").getAsJsonArray();
            assertEquals(3, items.size());
            assertEquals("1", items.get(0).getAsString());
            assertEquals("2", items.get(1).getAsString());
            assertEquals("3", items.get(2).getAsString());
            assertFalse(json.has("truncated"));
        }

        @Test
        void emptyArray() throws Exception {
            IJavaArray array = mockArray("String[]", 0);
            JsonObject json = toJson(collector.formatValue("items", array));
            assertEquals(0, json.get("length").getAsInt());
            assertEquals(0, json.get("value").getAsJsonArray().size());
        }

        @Test
        void arrayTruncatedAtFiveElements() throws Exception {
            IJavaArray array = mockArray("int[]", 10, "a", "b", "c", "d", "e");
            JsonObject json = toJson(collector.formatValue("big", array));
            assertEquals(10, json.get("length").getAsInt());
            JsonArray items = json.get("value").getAsJsonArray();
            assertEquals(5, items.size());
            assertTrue(json.get("truncated").getAsBoolean());
        }

        @Test
        void arrayExactlyFiveNotTruncated() throws Exception {
            IJavaArray array = mockArray("int[]", 5, "1", "2", "3", "4", "5");
            JsonObject json = toJson(collector.formatValue("exact", array));
            assertEquals(5, json.get("length").getAsInt());
            assertEquals(5, json.get("value").getAsJsonArray().size());
            assertFalse(json.has("truncated"));
        }

        @Test
        void arrayWithNullElements() throws Exception {
            IJavaArray array = mockArray("String[]", 2, "hello", "null");
            JsonObject json = toJson(collector.formatValue("mixed", array));
            JsonArray items = json.get("value").getAsJsonArray();
            assertEquals("hello", items.get(0).getAsString());
            assertEquals("null", items.get(1).getAsString());
        }

        @Test
        void wellKnownObjectUsesToString() throws Exception {
            IJavaObject obj = mockWellKnownObject("java.lang.Integer", "42");
            JsonObject json = toJson(collector.formatValue("boxed", obj));
            assertEquals("java.lang.Integer", json.get("type").getAsString());
            assertEquals("42", json.get("value").getAsString());
            assertFalse(json.has("fields"));
        }

        @Test
        void collectionObjectUsesToString() throws Exception {
            IJavaObject obj = mockWellKnownObject("java.util.ArrayList", "[one, two]");
            JsonObject json = toJson(collector.formatValue("list", obj));
            assertEquals("java.util.ArrayList", json.get("type").getAsString());
            assertEquals("[one, two]", json.get("value").getAsString());
        }

        @Test
        void mapObjectUsesToString() throws Exception {
            IJavaObject obj = mockWellKnownObject("java.util.HashMap", "{a=1, b=2}");
            JsonObject json = toJson(collector.formatValue("map", obj));
            assertEquals("java.util.HashMap", json.get("type").getAsString());
            assertEquals("{a=1, b=2}", json.get("value").getAsString());
        }

        @Test
        void wellKnownObjectFallsBackToValueStringWhenToStringFails() throws Exception {
            IJavaObject obj = mock(IJavaObject.class);
            when(obj.getReferenceTypeName()).thenReturn("java.lang.Integer");
            when(obj.getValueString()).thenReturn("42");
            when(obj.isNull()).thenReturn(false);

            IJavaThread thread = mock(IJavaThread.class);
            when(debugContext.resolveThread(null)).thenReturn(thread);
            when(obj.sendMessage(eq("toString"), any(), any(), any(), eq(false)))
                    .thenThrow(new RuntimeException("toString failed"));

            JsonObject json = toJson(collector.formatValue("val", obj));
            assertEquals("42", json.get("value").getAsString());
        }

        @Test
        void customObjectShowsValueStringAndFields() throws Exception {
            IJavaObject obj = mockObject("com.example.User", "User@abc123", "id", "name", "email");
            JsonObject json = toJson(collector.formatValue("user", obj));
            assertEquals("com.example.User", json.get("type").getAsString());
            assertEquals("User@abc123", json.get("value").getAsString());
            JsonArray fields = json.get("fields").getAsJsonArray();
            assertEquals(3, fields.size());
            assertEquals("id", fields.get(0).getAsString());
            assertEquals("name", fields.get(1).getAsString());
            assertEquals("email", fields.get(2).getAsString());
        }

        @Test
        void customObjectWithNoFieldsOmitsFieldsList() throws Exception {
            IJavaObject obj = mockObject("com.example.Empty", "Empty@1");
            JsonObject json = toJson(collector.formatValue("empty", obj));
            assertEquals("Empty@1", json.get("value").getAsString());
            assertFalse(json.has("fields"));
        }

        @Test
        void wellKnownObjectTruncatesToStringAt200Chars() throws Exception {
            String longString = "x".repeat(250);
            IJavaObject obj = mockWellKnownObject("java.lang.String", longString);
            JsonObject json = toJson(collector.formatValue("long", obj));
            String value = json.get("value").getAsString();
            assertEquals(200, value.length());
            assertTrue(value.endsWith("..."));
        }
    }

    // ---- collect tests ----

    @Nested
    class Collect {
        @Test
        void collectsMultipleVariables() throws Exception {
            IJavaValue intVal = mockPrimitive("int", "10");
            IJavaValue strVal = mockPrimitive("java.lang.String", "hello");
            IVariable v1 = mockVariable("count", intVal);
            IVariable v2 = mockVariable("name", strVal);
            IJavaStackFrame frame = mockFrameWithVariables(v1, v2);

            List<VariableResult> results = VariableCollector.collect(frame, debugContext);
            assertNotNull(results);
            assertEquals(2, results.size());

            JsonObject first = toJson(results.get(0));
            assertEquals("count", first.get("name").getAsString());
            assertEquals("10", first.get("value").getAsString());

            JsonObject second = toJson(results.get(1));
            assertEquals("name", second.get("name").getAsString());
            assertEquals("hello", second.get("value").getAsString());
        }

        @Test
        void emptyFrame() throws Exception {
            IJavaStackFrame frame = mockFrameWithVariables();
            List<VariableResult> results = VariableCollector.collect(frame, debugContext);
            assertNotNull(results);
            assertTrue(results.isEmpty());
        }

        @Test
        void skipsNonJavaValues() throws Exception {
            IVariable v = mock(IVariable.class);
            when(v.getName()).thenReturn("weird");
            when(v.getValue()).thenReturn(mock(org.eclipse.debug.core.model.IValue.class));
            IJavaStackFrame frame = mockFrameWithVariables(v);

            List<VariableResult> results = VariableCollector.collect(frame, debugContext);
            assertNotNull(results);
            assertTrue(results.isEmpty());
        }

        @Test
        void handlesDebugExceptionForIndividualVariable() throws Exception {
            IJavaValue goodVal = mockPrimitive("int", "5");
            IVariable good = mockVariable("x", goodVal);

            IVariable bad = mock(IVariable.class);
            when(bad.getName()).thenReturn("broken");
            when(bad.getValue()).thenThrow(new DebugException(
                    new org.eclipse.core.runtime.Status(
                            org.eclipse.core.runtime.IStatus.ERROR,
                            "test", "variable read failed")));

            IJavaStackFrame frame = mockFrameWithVariables(good, bad);

            List<VariableResult> results = VariableCollector.collect(frame, debugContext);
            assertNotNull(results);
            assertEquals(2, results.size());

            JsonObject first = toJson(results.get(0));
            assertEquals("x", first.get("name").getAsString());
            assertEquals("5", first.get("value").getAsString());

            JsonObject second = toJson(results.get(1));
            assertEquals("broken", second.get("name").getAsString());
            assertEquals("unknown", second.get("type").getAsString());
            assertTrue(second.get("value").getAsString().contains("error"));
        }

        @Test
        void returnsNullWhenFrameThrows() throws Exception {
            IJavaStackFrame frame = mock(IJavaStackFrame.class);
            when(frame.getVariables()).thenThrow(new RuntimeException("frame invalid"));

            List<VariableResult> results = VariableCollector.collect(frame, debugContext);
            assertNull(results);
        }

        @Test
        void mixedPrimitiveNullAndObjectVariables() throws Exception {
            IJavaValue intVal = mockPrimitive("int", "42");
            IJavaValue nullVal = mockNullValue("java.lang.Object");
            IJavaObject objVal = mockObject("com.example.Dto", "Dto@ff", "field1");

            IVariable v1 = mockVariable("x", intVal);
            IVariable v2 = mockVariable("y", nullVal);
            IVariable v3 = mockVariable("dto", objVal);
            IJavaStackFrame frame = mockFrameWithVariables(v1, v2, v3);

            List<VariableResult> results = VariableCollector.collect(frame, debugContext);
            assertNotNull(results);
            assertEquals(3, results.size());

            assertEquals("42", toJson(results.get(0)).get("value").getAsString());
            assertEquals("null", toJson(results.get(1)).get("value").getAsString());
            assertEquals("Dto@ff", toJson(results.get(2)).get("value").getAsString());
        }
    }

    // ---- collectForCurrentFrame tests ----

    @Nested
    class CollectForCurrentFrame {
        @Test
        void delegatesToCollect() throws Exception {
            IJavaThread thread = mock(IJavaThread.class);
            when(debugContext.resolveThread(null)).thenReturn(thread);

            IJavaValue val = mockPrimitive("int", "7");
            IVariable v = mockVariable("n", val);
            IJavaStackFrame frame = mockFrameWithVariables(v);
            when(debugContext.resolveFrame(thread, null)).thenReturn(frame);

            List<VariableResult> results = VariableCollector.collectForCurrentFrame(debugContext);
            assertNotNull(results);
            assertEquals(1, results.size());
            assertEquals("n", toJson(results.get(0)).get("name").getAsString());
        }

        @Test
        void returnsNullWhenResolveThreadFails() throws Exception {
            when(debugContext.resolveThread(null)).thenThrow(new IllegalStateException("no thread"));
            assertNull(VariableCollector.collectForCurrentFrame(debugContext));
        }

        @Test
        void returnsNullWhenResolveFrameFails() throws Exception {
            IJavaThread thread = mock(IJavaThread.class);
            when(debugContext.resolveThread(null)).thenReturn(thread);
            when(debugContext.resolveFrame(thread, null)).thenThrow(new IllegalStateException("no frame"));
            assertNull(VariableCollector.collectForCurrentFrame(debugContext));
        }

        @Test
        void returnsNullWhenFrameVariablesThrow() throws Exception {
            IJavaThread thread = mock(IJavaThread.class);
            when(debugContext.resolveThread(null)).thenReturn(thread);
            IJavaStackFrame frame = mock(IJavaStackFrame.class);
            when(frame.getVariables()).thenThrow(new RuntimeException("stale frame"));
            when(debugContext.resolveFrame(thread, null)).thenReturn(frame);

            assertNull(VariableCollector.collectForCurrentFrame(debugContext));
        }

        @Test
        void returnsEmptyListForEmptyFrame() throws Exception {
            IJavaThread thread = mock(IJavaThread.class);
            when(debugContext.resolveThread(null)).thenReturn(thread);
            IJavaStackFrame frame = mockFrameWithVariables();
            when(debugContext.resolveFrame(thread, null)).thenReturn(frame);

            List<VariableResult> results = VariableCollector.collectForCurrentFrame(debugContext);
            assertNotNull(results);
            assertTrue(results.isEmpty());
        }
    }
}
