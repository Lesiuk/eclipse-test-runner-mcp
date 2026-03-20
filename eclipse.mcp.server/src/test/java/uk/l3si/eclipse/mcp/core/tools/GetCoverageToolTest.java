package uk.l3si.eclipse.mcp.core.tools;

import uk.l3si.eclipse.mcp.model.CoverageResult;
import uk.l3si.eclipse.mcp.tools.Args;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GetCoverageToolTest {

    private static final Gson GSON = new Gson();

    @Test
    void nameIsGetCoverage() {
        assertEquals("get_coverage", new GetCoverageTool().getName());
    }

    @Test
    void missingClassThrows() {
        GetCoverageTool tool = new GetCoverageTool();
        JsonObject args = new JsonObject();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args), message -> {}));
        assertTrue(ex.getMessage().contains("class"));
    }

    @Test
    void delegatesToCoverageHelper() throws Exception {
        try (MockedStatic<CoverageHelper> ch = mockStatic(CoverageHelper.class)) {
            CoverageResult mockResult = CoverageResult.builder()
                    .className("com.example.MyService")
                    .methods(Collections.emptyList())
                    .lines(Collections.emptyList())
                    .build();

            ch.when(() -> CoverageHelper.getCoverageForClass("com.example.MyService"))
                    .thenReturn(mockResult);

            GetCoverageTool tool = new GetCoverageTool();
            JsonObject args = new JsonObject();
            args.addProperty("class", "com.example.MyService");

            Object result = tool.execute(new Args(args), message -> {});
            assertSame(mockResult, result);

            ch.verify(() -> CoverageHelper.getCoverageForClass("com.example.MyService"));
        }
    }

    @Test
    void coverageHelperThrowsPropagates() {
        try (MockedStatic<CoverageHelper> ch = mockStatic(CoverageHelper.class)) {
            ch.when(() -> CoverageHelper.getCoverageForClass("com.example.Missing"))
                    .thenThrow(new IllegalStateException("No coverage data available"));

            GetCoverageTool tool = new GetCoverageTool();
            JsonObject args = new JsonObject();
            args.addProperty("class", "com.example.Missing");

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> tool.execute(new Args(args), message -> {}));
            assertTrue(ex.getMessage().contains("No coverage data available"));
        }
    }
}
