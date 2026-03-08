package eclipse.mcp.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

public class Args {

    private final JsonObject json;

    public Args(JsonObject arguments) {
        this.json = arguments != null ? arguments : new JsonObject();
    }

    public String getString(String key) {
        return json.has(key) && !json.get(key).isJsonNull()
                ? json.get(key).getAsString() : null;
    }

    public String getString(String key, String defaultValue) {
        String value = getString(key);
        return value != null ? value : defaultValue;
    }

    public String requireString(String key, String description) {
        String value = getString(key);
        if (value == null) {
            throw new IllegalArgumentException(
                    "Missing required parameter: '" + key + "' (" + description + ")");
        }
        return value;
    }

    public boolean getBoolean(String key) {
        return json.has(key) && !json.get(key).isJsonNull()
                && json.get(key).getAsBoolean();
    }

    public List<String> getStringList(String key) {
        if (!json.has(key) || json.get(key).isJsonNull()) return null;
        JsonArray array = json.getAsJsonArray(key);
        List<String> list = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            list.add(array.get(i).getAsString());
        }
        return list;
    }
}
