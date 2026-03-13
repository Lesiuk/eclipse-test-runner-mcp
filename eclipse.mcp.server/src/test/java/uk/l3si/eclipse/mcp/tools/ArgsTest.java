package uk.l3si.eclipse.mcp.tools;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ArgsTest {

    @Test
    void getStringTrimsWhitespace() {
        JsonObject json = new JsonObject();
        json.addProperty("key", "  hello  ");
        Args args = new Args(json);
        assertEquals("hello", args.getString("key"));
    }

    @Test
    void getStringReturnsNullForBlank() {
        JsonObject json = new JsonObject();
        json.addProperty("key", "   ");
        Args args = new Args(json);
        assertNull(args.getString("key"));
    }

    @Test
    void getStringReturnsNullForEmpty() {
        JsonObject json = new JsonObject();
        json.addProperty("key", "");
        Args args = new Args(json);
        assertNull(args.getString("key"));
    }

    @Test
    void getStringReturnsNullForMissing() {
        Args args = new Args(new JsonObject());
        assertNull(args.getString("key"));
    }

    @Test
    void requireStringTrimsWhitespace() {
        JsonObject json = new JsonObject();
        json.addProperty("key", "  value  ");
        Args args = new Args(json);
        assertEquals("value", args.requireString("key", "desc"));
    }

    @Test
    void requireStringRejectsBlank() {
        JsonObject json = new JsonObject();
        json.addProperty("key", "   ");
        Args args = new Args(json);
        assertThrows(IllegalArgumentException.class,
                () -> args.requireString("key", "desc"));
    }

    @Test
    void getStringDefaultUsedForBlank() {
        JsonObject json = new JsonObject();
        json.addProperty("key", "  ");
        Args args = new Args(json);
        assertEquals("fallback", args.getString("key", "fallback"));
    }
}
