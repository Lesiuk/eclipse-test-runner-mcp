package eclipse.mcp.model;

import lombok.Builder;

@Builder
public class ConsoleOutputResult {
    private String configName;
    private boolean terminated;
    private String stdout;
    private String stderr;
}
