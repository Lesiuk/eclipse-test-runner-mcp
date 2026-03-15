package uk.l3si.eclipse.mcp.debugging.model;

import java.util.List;
import lombok.Builder;

@Builder
public class StackTraceResult {
    private String thread;
    private List<FrameInfo> frames;
}
