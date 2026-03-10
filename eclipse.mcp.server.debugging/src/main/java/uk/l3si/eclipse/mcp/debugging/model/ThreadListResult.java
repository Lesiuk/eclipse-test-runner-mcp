package uk.l3si.eclipse.mcp.debugging.model;

import java.util.List;
import lombok.Builder;

@Builder
public class ThreadListResult {
    private List<ThreadInfo> threads;
}
