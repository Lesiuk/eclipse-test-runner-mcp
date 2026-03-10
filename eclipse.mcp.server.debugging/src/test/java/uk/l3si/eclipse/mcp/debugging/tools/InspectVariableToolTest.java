package uk.l3si.eclipse.mcp.debugging.tools;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.eclipse.debug.core.model.IVariable;
import org.eclipse.jdt.debug.core.IJavaArray;
import org.eclipse.jdt.debug.core.IJavaObject;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaThread;
import org.eclipse.jdt.debug.core.IJavaValue;
import org.eclipse.jdt.debug.core.IJavaVariable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.l3si.eclipse.mcp.debugging.DebugContext;
import uk.l3si.eclipse.mcp.tools.Args;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InspectVariableToolTest {

    private static final Gson GSON = new Gson();

    private DebugContext debugContext;
    private InspectVariableTool tool;

    @BeforeEach
    void setUp() {
        debugContext = mock(DebugContext.class);
        tool = new InspectVariableTool(debugContext);
    }

    private JsonObject executeAndSerialize(JsonObject args) throws Exception {
        return GSON.toJsonTree(tool.execute(new Args(args))).getAsJsonObject();
    }

    @Test
    void nameIsInspectVariable() {
        assertEquals("inspect_variable", tool.getName());
    }

    @Test
    void missingNameThrows() {
        JsonObject args = new JsonObject();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("name"));
    }

    @Test
    void blankNameThrows() {
        JsonObject args = new JsonObject();
        args.addProperty("name", "   ");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("blank"));
    }

    @Test
    void primitiveVariable() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        IJavaStackFrame frame = mock(IJavaStackFrame.class);
        when(debugContext.resolveThread(null)).thenReturn(thread);
        when(debugContext.resolveFrame(thread, null)).thenReturn(frame);

        IJavaVariable variable = mock(IJavaVariable.class);
        IJavaValue value = mock(IJavaValue.class);
        when(frame.findVariable("x")).thenReturn(variable);
        when(variable.getValue()).thenReturn(value);
        when(value.getReferenceTypeName()).thenReturn("int");
        when(value.isNull()).thenReturn(false);
        when(value.getValueString()).thenReturn("42");
        when(value.getVariables()).thenReturn(new IVariable[]{});

        JsonObject args = new JsonObject();
        args.addProperty("name", "x");

        JsonObject result = executeAndSerialize(args);
        assertEquals("x", result.get("name").getAsString());
        assertEquals("int", result.get("type").getAsString());
        assertEquals("42", result.get("value").getAsString());
    }

    @Test
    void nullVariable() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        IJavaStackFrame frame = mock(IJavaStackFrame.class);
        when(debugContext.resolveThread(null)).thenReturn(thread);
        when(debugContext.resolveFrame(thread, null)).thenReturn(frame);

        IJavaVariable variable = mock(IJavaVariable.class);
        IJavaValue value = mock(IJavaValue.class);
        when(frame.findVariable("obj")).thenReturn(variable);
        when(variable.getValue()).thenReturn(value);
        when(value.getReferenceTypeName()).thenReturn("java.lang.Object");
        when(value.isNull()).thenReturn(true);
        when(value.getVariables()).thenReturn(new IVariable[]{});

        JsonObject args = new JsonObject();
        args.addProperty("name", "obj");

        JsonObject result = executeAndSerialize(args);
        assertEquals("null", result.get("value").getAsString());
    }

    private interface IJavaObjectValue extends IJavaObject, IJavaValue {}

    @Test
    void objectVariable() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        IJavaStackFrame frame = mock(IJavaStackFrame.class);
        when(debugContext.resolveThread(null)).thenReturn(thread);
        when(debugContext.resolveFrame(thread, null)).thenReturn(frame);

        IJavaVariable variable = mock(IJavaVariable.class);
        IJavaObjectValue value = mock(IJavaObjectValue.class);
        when(frame.findVariable("myObj")).thenReturn(variable);
        when(variable.getValue()).thenReturn(value);
        when(value.getReferenceTypeName()).thenReturn("com.example.MyObj");
        when(value.isNull()).thenReturn(false);
        when(value.getValueString()).thenReturn("MyObj@abc123");

        IVariable field1 = mock(IVariable.class);
        when(field1.getName()).thenReturn("name");
        IVariable field2 = mock(IVariable.class);
        when(field2.getName()).thenReturn("age");
        when(value.getVariables()).thenReturn(new IVariable[]{field1, field2});

        JsonObject args = new JsonObject();
        args.addProperty("name", "myObj");

        JsonObject result = executeAndSerialize(args);
        assertEquals("com.example.MyObj", result.get("type").getAsString());
        assertEquals("MyObj@abc123", result.get("value").getAsString());

        JsonArray fields = result.getAsJsonArray("fields");
        assertEquals(2, fields.size());
        assertEquals("name", fields.get(0).getAsString());
        assertEquals("age", fields.get(1).getAsString());
    }

    private interface IJavaArrayValue extends IJavaArray, IJavaValue {}

    @Test
    void arrayVariable() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        IJavaStackFrame frame = mock(IJavaStackFrame.class);
        when(debugContext.resolveThread(null)).thenReturn(thread);
        when(debugContext.resolveFrame(thread, null)).thenReturn(frame);

        IJavaVariable variable = mock(IJavaVariable.class);
        IJavaArrayValue array = mock(IJavaArrayValue.class);
        when(frame.findVariable("arr")).thenReturn(variable);
        when(variable.getValue()).thenReturn(array);
        when(array.getReferenceTypeName()).thenReturn("int[]");
        when(array.isNull()).thenReturn(false);
        when(array.getLength()).thenReturn(3);
        when(array.getVariables()).thenReturn(new IVariable[]{});

        IJavaValue elem0 = mock(IJavaValue.class);
        when(elem0.getReferenceTypeName()).thenReturn("int");
        when(elem0.getValueString()).thenReturn("10");
        IJavaValue elem1 = mock(IJavaValue.class);
        when(elem1.getReferenceTypeName()).thenReturn("int");
        when(elem1.getValueString()).thenReturn("20");
        IJavaValue elem2 = mock(IJavaValue.class);
        when(elem2.getReferenceTypeName()).thenReturn("int");
        when(elem2.getValueString()).thenReturn("30");
        when(array.getValue(0)).thenReturn(elem0);
        when(array.getValue(1)).thenReturn(elem1);
        when(array.getValue(2)).thenReturn(elem2);

        JsonObject args = new JsonObject();
        args.addProperty("name", "arr");

        JsonObject result = executeAndSerialize(args);
        assertEquals("int[]", result.get("type").getAsString());
        assertEquals(3, result.get("length").getAsInt());

        JsonArray elements = result.getAsJsonArray("elements");
        assertEquals(3, elements.size());
        assertEquals("10", elements.get(0).getAsJsonObject().get("value").getAsString());
        assertEquals("20", elements.get(1).getAsJsonObject().get("value").getAsString());
        assertEquals("30", elements.get(2).getAsJsonObject().get("value").getAsString());
    }

    @Test
    void variableNotFoundThrows() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        IJavaStackFrame frame = mock(IJavaStackFrame.class);
        when(debugContext.resolveThread(null)).thenReturn(thread);
        when(debugContext.resolveFrame(thread, null)).thenReturn(frame);

        when(frame.findVariable("nonexistent")).thenReturn(null);
        when(frame.getVariables()).thenReturn(new IVariable[]{});

        JsonObject args = new JsonObject();
        args.addProperty("name", "nonexistent");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("nonexistent"));
    }

    @Test
    void dotPathNavigation() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        IJavaStackFrame frame = mock(IJavaStackFrame.class);
        when(debugContext.resolveThread(null)).thenReturn(thread);
        when(debugContext.resolveFrame(thread, null)).thenReturn(frame);

        // myObj -> field "name"
        IJavaVariable objVar = mock(IJavaVariable.class);
        IJavaObjectValue objValue = mock(IJavaObjectValue.class);
        when(frame.findVariable("myObj")).thenReturn(objVar);
        when(objVar.getValue()).thenReturn(objValue);
        when(objValue.isNull()).thenReturn(false);

        IJavaVariable nameVar = mock(IJavaVariable.class);
        when(nameVar.getName()).thenReturn("name");
        IJavaValue nameValue = mock(IJavaValue.class);
        when(nameVar.getValue()).thenReturn(nameValue);
        when(nameValue.getReferenceTypeName()).thenReturn("java.lang.String");
        when(nameValue.isNull()).thenReturn(false);
        when(nameValue.getValueString()).thenReturn("\"hello\"");
        when(nameValue.getVariables()).thenReturn(new IVariable[]{});

        when(objValue.getVariables()).thenReturn(new IVariable[]{nameVar});

        JsonObject args = new JsonObject();
        args.addProperty("name", "myObj.name");

        JsonObject result = executeAndSerialize(args);
        assertEquals("java.lang.String", result.get("type").getAsString());
        assertEquals("\"hello\"", result.get("value").getAsString());
    }

    @Test
    void arrayIndexAccess() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        IJavaStackFrame frame = mock(IJavaStackFrame.class);
        when(debugContext.resolveThread(null)).thenReturn(thread);
        when(debugContext.resolveFrame(thread, null)).thenReturn(frame);

        IJavaVariable arrVar = mock(IJavaVariable.class);
        IJavaArrayValue arrValue = mock(IJavaArrayValue.class);
        when(frame.findVariable("items")).thenReturn(arrVar);
        when(arrVar.getValue()).thenReturn(arrValue);
        when(arrValue.isNull()).thenReturn(false);
        when(arrValue.getLength()).thenReturn(5);

        IJavaValue elemValue = mock(IJavaValue.class);
        when(arrValue.getValue(2)).thenReturn(elemValue);
        when(elemValue.getReferenceTypeName()).thenReturn("java.lang.String");
        when(elemValue.isNull()).thenReturn(false);
        when(elemValue.getValueString()).thenReturn("\"world\"");
        when(elemValue.getVariables()).thenReturn(new IVariable[]{});

        JsonObject args = new JsonObject();
        args.addProperty("name", "items[2]");

        JsonObject result = executeAndSerialize(args);
        assertEquals("java.lang.String", result.get("type").getAsString());
        assertEquals("\"world\"", result.get("value").getAsString());
    }

    @Test
    void arrayIndexOutOfBoundsThrows() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        IJavaStackFrame frame = mock(IJavaStackFrame.class);
        when(debugContext.resolveThread(null)).thenReturn(thread);
        when(debugContext.resolveFrame(thread, null)).thenReturn(frame);

        IJavaVariable arrVar = mock(IJavaVariable.class);
        IJavaArrayValue arrValue = mock(IJavaArrayValue.class);
        when(frame.findVariable("arr")).thenReturn(arrVar);
        when(arrVar.getValue()).thenReturn(arrValue);
        when(arrValue.isNull()).thenReturn(false);
        when(arrValue.getLength()).thenReturn(3);

        JsonObject args = new JsonObject();
        args.addProperty("name", "arr[10]");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("out of bounds"));
    }

    @Test
    void withThreadIdAndFrameIndex() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        IJavaStackFrame frame = mock(IJavaStackFrame.class);
        when(debugContext.resolveThread(99L)).thenReturn(thread);
        when(debugContext.resolveFrame(thread, 2)).thenReturn(frame);

        IJavaVariable variable = mock(IJavaVariable.class);
        IJavaValue value = mock(IJavaValue.class);
        when(frame.findVariable("y")).thenReturn(variable);
        when(variable.getValue()).thenReturn(value);
        when(value.getReferenceTypeName()).thenReturn("double");
        when(value.isNull()).thenReturn(false);
        when(value.getValueString()).thenReturn("3.14");
        when(value.getVariables()).thenReturn(new IVariable[]{});

        JsonObject args = new JsonObject();
        args.addProperty("name", "y");
        args.addProperty("thread_id", "99");
        args.addProperty("frame_index", "2");

        JsonObject result = executeAndSerialize(args);
        assertEquals("3.14", result.get("value").getAsString());
        verify(debugContext).resolveThread(99L);
        verify(debugContext).resolveFrame(thread, 2);
    }

    @Test
    void noSessionThrows() throws Exception {
        when(debugContext.resolveThread(null))
                .thenThrow(new IllegalStateException("No debug session active. Launch a test with mode='debug' first."));

        JsonObject args = new JsonObject();
        args.addProperty("name", "x");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("No debug session"));
    }

    @Test
    void fieldNotFoundThrows() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        IJavaStackFrame frame = mock(IJavaStackFrame.class);
        when(debugContext.resolveThread(null)).thenReturn(thread);
        when(debugContext.resolveFrame(thread, null)).thenReturn(frame);

        IJavaVariable objVar = mock(IJavaVariable.class);
        IJavaObjectValue objValue = mock(IJavaObjectValue.class);
        when(frame.findVariable("myObj")).thenReturn(objVar);
        when(objVar.getValue()).thenReturn(objValue);
        when(objValue.isNull()).thenReturn(false);
        when(objValue.getVariables()).thenReturn(new IVariable[]{});

        JsonObject args = new JsonObject();
        args.addProperty("name", "myObj.nonexistent");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("nonexistent"));
    }
}
