package uk.l3si.eclipse.mcp.core.tools;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.eclipse.eclemma.core.CoverageTools;
import org.eclipse.eclemma.core.analysis.IJavaModelCoverage;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.jacoco.core.analysis.ICounter;
import org.jacoco.core.analysis.ICoverageNode;
import org.jacoco.core.analysis.ILine;
import org.jacoco.core.analysis.ISourceNode;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("restriction")
class CoverageHelperTest {

    private static final Gson GSON = new Gson();

    interface SourceCoverageNode extends ICoverageNode, ISourceNode {}

    /**
     * Creates a fully-stubbed ICounter mock. Must NOT be called inside another
     * when().thenReturn() chain — Mockito does not allow nested stubbing.
     */
    private static ICounter mockCounter(int covered, int missed) {
        ICounter counter = mock(ICounter.class);
        when(counter.getCoveredCount()).thenReturn(covered);
        when(counter.getMissedCount()).thenReturn(missed);
        when(counter.getTotalCount()).thenReturn(covered + missed);
        return counter;
    }

    /**
     * Creates a fully-stubbed ILine mock. Branch/instruction counters must be
     * pre-created (not built inline) to avoid nested-stubbing issues.
     */
    private static ILine mockLine(int status, ICounter branchCounter, ICounter instructionCounter) {
        ILine line = mock(ILine.class);
        when(line.getStatus()).thenReturn(status);
        when(line.getBranchCounter()).thenReturn(branchCounter);
        when(line.getInstructionCounter()).thenReturn(instructionCounter);
        return line;
    }

    @Test
    void noCoverageDataThrows() {
        try (MockedStatic<CoverageTools> mocked = mockStatic(CoverageTools.class)) {
            mocked.when(CoverageTools::getJavaModelCoverage).thenReturn(null);

            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    () -> CoverageHelper.getCoverageForClass("com.example.Foo"));
            assertTrue(ex.getMessage().contains("No coverage data available"));
        }
    }

    @Test
    void classNotFoundThrows() throws Exception {
        IJavaModelCoverage modelCoverage = mock(IJavaModelCoverage.class);
        IJavaProject project = mock(IJavaProject.class);
        when(project.findType("com.example.Foo")).thenReturn(null);
        when(modelCoverage.getProjects()).thenReturn(new IJavaProject[]{project});

        try (MockedStatic<CoverageTools> mocked = mockStatic(CoverageTools.class)) {
            mocked.when(CoverageTools::getJavaModelCoverage).thenReturn(modelCoverage);

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> CoverageHelper.getCoverageForClass("com.example.Foo"));
            assertTrue(ex.getMessage().contains("not found in any project"));
        }
    }

    @Test
    void noCoverageForClassThrows() throws Exception {
        IJavaModelCoverage modelCoverage = mock(IJavaModelCoverage.class);
        IJavaProject project = mock(IJavaProject.class);
        IType type = mock(IType.class);
        when(type.exists()).thenReturn(true);
        when(project.findType("com.example.Foo")).thenReturn(type);
        when(modelCoverage.getProjects()).thenReturn(new IJavaProject[]{project});
        when(modelCoverage.getCoverageFor(type)).thenReturn(null);

        try (MockedStatic<CoverageTools> mocked = mockStatic(CoverageTools.class)) {
            mocked.when(CoverageTools::getJavaModelCoverage).thenReturn(modelCoverage);

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> CoverageHelper.getCoverageForClass("com.example.Foo"));
            assertTrue(ex.getMessage().contains("No coverage data for class"));
        }
    }

    @Test
    void successfulCoverageReturnsSummaryMethodsLines() throws Exception {
        String className = "com.example.Foo";

        // Pre-create all counters to avoid nested-stubbing issues
        ICounter lineCounter = mockCounter(8, 2);
        ICounter branchCounter = mockCounter(3, 1);
        ICounter methodCounter = mockCounter(5, 0);

        ICounter zeroBranch = mockCounter(0, 0);
        ICounter zeroInstr = mockCounter(0, 0);
        ICounter partlyBranch = mockCounter(1, 1);

        // Pre-create lines
        ICounter lb1 = mockCounter(0, 0);
        ICounter li1 = mockCounter(0, 0);
        ILine line10 = mockLine(ICounter.FULLY_COVERED, lb1, li1);

        ICounter lb2 = mockCounter(0, 0);
        ICounter li2 = mockCounter(0, 0);
        ILine line11 = mockLine(ICounter.NOT_COVERED, lb2, li2);

        ICounter li3 = mockCounter(0, 0);
        ILine line12 = mockLine(ICounter.PARTLY_COVERED, partlyBranch, li3);

        ICounter lb4 = mockCounter(0, 0);
        ICounter li4 = mockCounter(0, 0);
        ILine line13 = mockLine(ICounter.EMPTY, lb4, li4);

        // Method counters
        ICounter mLineCounter = mockCounter(4, 1);
        ICounter mBranchCounter = mockCounter(2, 0);

        // Method lines
        ICounter mlb1 = mockCounter(0, 0);
        ICounter mli1 = mockCounter(0, 0);
        ILine mLine10 = mockLine(ICounter.FULLY_COVERED, mlb1, mli1);

        ICounter mlb2 = mockCounter(0, 0);
        ICounter mli2 = mockCounter(0, 0);
        ILine mLine11 = mockLine(ICounter.NOT_COVERED, mlb2, mli2);

        ICounter mlb3 = mockCounter(0, 0);
        ICounter mli3 = mockCounter(0, 0);
        ILine mLine12 = mockLine(ICounter.PARTLY_COVERED, mlb3, mli3);

        // Now wire up the mocks
        IJavaModelCoverage modelCoverage = mock(IJavaModelCoverage.class);
        IJavaProject project = mock(IJavaProject.class);
        IType type = mock(IType.class);
        when(type.exists()).thenReturn(true);
        when(project.findType(className)).thenReturn(type);
        when(modelCoverage.getProjects()).thenReturn(new IJavaProject[]{project});

        SourceCoverageNode classCoverage = mock(SourceCoverageNode.class);
        when(modelCoverage.getCoverageFor(type)).thenReturn(classCoverage);

        when(classCoverage.getLineCounter()).thenReturn(lineCounter);
        when(classCoverage.getBranchCounter()).thenReturn(branchCounter);
        when(classCoverage.getMethodCounter()).thenReturn(methodCounter);

        when(classCoverage.getFirstLine()).thenReturn(10);
        when(classCoverage.getLastLine()).thenReturn(13);
        when(classCoverage.getLine(10)).thenReturn(line10);
        when(classCoverage.getLine(11)).thenReturn(line11);
        when(classCoverage.getLine(12)).thenReturn(line12);
        when(classCoverage.getLine(13)).thenReturn(line13);

        IMethod method = mock(IMethod.class);
        when(method.getElementName()).thenReturn("doStuff");
        when(type.getMethods()).thenReturn(new IMethod[]{method});

        SourceCoverageNode methodCoverageNode = mock(SourceCoverageNode.class);
        when(methodCoverageNode.getLineCounter()).thenReturn(mLineCounter);
        when(methodCoverageNode.getBranchCounter()).thenReturn(mBranchCounter);
        when(methodCoverageNode.getFirstLine()).thenReturn(10);
        when(methodCoverageNode.getLastLine()).thenReturn(12);
        when(methodCoverageNode.getLine(10)).thenReturn(mLine10);
        when(methodCoverageNode.getLine(11)).thenReturn(mLine11);
        when(methodCoverageNode.getLine(12)).thenReturn(mLine12);

        try (MockedStatic<CoverageTools> mocked = mockStatic(CoverageTools.class)) {
            mocked.when(CoverageTools::getJavaModelCoverage).thenReturn(modelCoverage);
            mocked.when(() -> CoverageTools.getCoverageInfo(method)).thenReturn(methodCoverageNode);

            JsonObject result = GSON.toJsonTree(CoverageHelper.getCoverageForClass(className)).getAsJsonObject();

            // summary
            JsonObject summary = result.getAsJsonObject("summary");
            assertEquals("8/10 (80.0%)", summary.get("lineCoverage").getAsString());
            assertEquals("3/4 (75.0%)", summary.get("branchCoverage").getAsString());
            assertEquals("5/5 (100.0%)", summary.get("methodCoverage").getAsString());

            // methods
            JsonArray methods = result.getAsJsonArray("methods");
            assertEquals(1, methods.size());
            JsonObject m = methods.get(0).getAsJsonObject();
            assertEquals("doStuff", m.get("name").getAsString());
            assertEquals("4/5 (80.0%)", m.get("lineCoverage").getAsString());
            assertEquals("2/2 (100.0%)", m.get("branchCoverage").getAsString());

            // lines (EMPTY skipped, so 3 entries)
            JsonArray lines = result.getAsJsonArray("lines");
            assertEquals(3, lines.size());

            assertEquals(10, lines.get(0).getAsJsonObject().get("line").getAsInt());
            assertEquals("COVERED", lines.get(0).getAsJsonObject().get("status").getAsString());

            assertEquals(11, lines.get(1).getAsJsonObject().get("line").getAsInt());
            assertEquals("NOT_COVERED", lines.get(1).getAsJsonObject().get("status").getAsString());

            assertEquals(12, lines.get(2).getAsJsonObject().get("line").getAsInt());
            assertEquals("PARTLY_COVERED", lines.get(2).getAsJsonObject().get("status").getAsString());
            assertTrue(lines.get(2).getAsJsonObject().has("branches"));
            assertEquals("1/2", lines.get(2).getAsJsonObject().get("branches").getAsString());
        }
    }

    @Test
    void methodWithUncoveredLinesListsThem() throws Exception {
        String className = "com.example.Bar";

        // Pre-create all counters
        ICounter classLine = mockCounter(1, 1);
        ICounter classBranch = mockCounter(0, 0);
        ICounter classMethod = mockCounter(1, 0);
        ICounter mLine = mockCounter(2, 1);
        ICounter mBranch = mockCounter(0, 0);

        ICounter lb1 = mockCounter(0, 0);
        ICounter li1 = mockCounter(0, 0);
        ILine line20 = mockLine(ICounter.FULLY_COVERED, lb1, li1);

        ICounter lb2 = mockCounter(0, 0);
        ICounter li2 = mockCounter(0, 0);
        ILine line21 = mockLine(ICounter.NOT_COVERED, lb2, li2);

        ICounter lb3 = mockCounter(0, 0);
        ICounter li3 = mockCounter(0, 0);
        ILine line22 = mockLine(ICounter.FULLY_COVERED, lb3, li3);

        // Wire up
        IJavaModelCoverage modelCoverage = mock(IJavaModelCoverage.class);
        IJavaProject project = mock(IJavaProject.class);
        IType type = mock(IType.class);
        when(type.exists()).thenReturn(true);
        when(project.findType(className)).thenReturn(type);
        when(modelCoverage.getProjects()).thenReturn(new IJavaProject[]{project});

        SourceCoverageNode classCoverage = mock(SourceCoverageNode.class);
        when(modelCoverage.getCoverageFor(type)).thenReturn(classCoverage);
        when(classCoverage.getLineCounter()).thenReturn(classLine);
        when(classCoverage.getBranchCounter()).thenReturn(classBranch);
        when(classCoverage.getMethodCounter()).thenReturn(classMethod);
        when(classCoverage.getFirstLine()).thenReturn(ISourceNode.UNKNOWN_LINE);

        IMethod method = mock(IMethod.class);
        when(method.getElementName()).thenReturn("process");
        when(type.getMethods()).thenReturn(new IMethod[]{method});

        SourceCoverageNode methodCoverage = mock(SourceCoverageNode.class);
        when(methodCoverage.getLineCounter()).thenReturn(mLine);
        when(methodCoverage.getBranchCounter()).thenReturn(mBranch);
        when(methodCoverage.getFirstLine()).thenReturn(20);
        when(methodCoverage.getLastLine()).thenReturn(22);
        when(methodCoverage.getLine(20)).thenReturn(line20);
        when(methodCoverage.getLine(21)).thenReturn(line21);
        when(methodCoverage.getLine(22)).thenReturn(line22);

        try (MockedStatic<CoverageTools> mocked = mockStatic(CoverageTools.class)) {
            mocked.when(CoverageTools::getJavaModelCoverage).thenReturn(modelCoverage);
            mocked.when(() -> CoverageTools.getCoverageInfo(method)).thenReturn(methodCoverage);

            JsonObject result = GSON.toJsonTree(CoverageHelper.getCoverageForClass(className)).getAsJsonObject();

            JsonArray methods = result.getAsJsonArray("methods");
            assertEquals(1, methods.size());
            JsonObject m = methods.get(0).getAsJsonObject();
            JsonArray uncovered = m.getAsJsonArray("uncoveredLines");
            assertNotNull(uncovered);
            assertEquals(1, uncovered.size());
            assertEquals(21, uncovered.get(0).getAsInt());
        }
    }

    @Test
    void fullyCoveredMethodHasNoUncoveredLines() throws Exception {
        String className = "com.example.Baz";

        // Pre-create all counters
        ICounter classLine = mockCounter(3, 0);
        ICounter classBranch = mockCounter(0, 0);
        ICounter classMethod = mockCounter(1, 0);
        ICounter mLine = mockCounter(3, 0);
        ICounter mBranch = mockCounter(0, 0);

        ICounter lb1 = mockCounter(0, 0);
        ICounter li1 = mockCounter(0, 0);
        ILine line5 = mockLine(ICounter.FULLY_COVERED, lb1, li1);

        ICounter lb2 = mockCounter(0, 0);
        ICounter li2 = mockCounter(0, 0);
        ILine line6 = mockLine(ICounter.FULLY_COVERED, lb2, li2);

        ICounter lb3 = mockCounter(0, 0);
        ICounter li3 = mockCounter(0, 0);
        ILine line7 = mockLine(ICounter.FULLY_COVERED, lb3, li3);

        // Wire up
        IJavaModelCoverage modelCoverage = mock(IJavaModelCoverage.class);
        IJavaProject project = mock(IJavaProject.class);
        IType type = mock(IType.class);
        when(type.exists()).thenReturn(true);
        when(project.findType(className)).thenReturn(type);
        when(modelCoverage.getProjects()).thenReturn(new IJavaProject[]{project});

        SourceCoverageNode classCoverage = mock(SourceCoverageNode.class);
        when(modelCoverage.getCoverageFor(type)).thenReturn(classCoverage);
        when(classCoverage.getLineCounter()).thenReturn(classLine);
        when(classCoverage.getBranchCounter()).thenReturn(classBranch);
        when(classCoverage.getMethodCounter()).thenReturn(classMethod);
        when(classCoverage.getFirstLine()).thenReturn(ISourceNode.UNKNOWN_LINE);

        IMethod method = mock(IMethod.class);
        when(method.getElementName()).thenReturn("allGreen");
        when(type.getMethods()).thenReturn(new IMethod[]{method});

        SourceCoverageNode methodCoverage = mock(SourceCoverageNode.class);
        when(methodCoverage.getLineCounter()).thenReturn(mLine);
        when(methodCoverage.getBranchCounter()).thenReturn(mBranch);
        when(methodCoverage.getFirstLine()).thenReturn(5);
        when(methodCoverage.getLastLine()).thenReturn(7);
        when(methodCoverage.getLine(5)).thenReturn(line5);
        when(methodCoverage.getLine(6)).thenReturn(line6);
        when(methodCoverage.getLine(7)).thenReturn(line7);

        try (MockedStatic<CoverageTools> mocked = mockStatic(CoverageTools.class)) {
            mocked.when(CoverageTools::getJavaModelCoverage).thenReturn(modelCoverage);
            mocked.when(() -> CoverageTools.getCoverageInfo(method)).thenReturn(methodCoverage);

            JsonObject result = GSON.toJsonTree(CoverageHelper.getCoverageForClass(className)).getAsJsonObject();

            JsonArray methods = result.getAsJsonArray("methods");
            assertEquals(1, methods.size());
            assertNull(methods.get(0).getAsJsonObject().get("uncoveredLines"));
        }
    }

    @Test
    void branchInfoIncludedForPartialLines() throws Exception {
        String className = "com.example.BranchTest";

        // Pre-create all counters
        ICounter classLine = mockCounter(1, 0);
        ICounter classBranch = mockCounter(1, 1);
        ICounter classMethod = mockCounter(1, 0);
        ICounter lineBranch = mockCounter(1, 1); // 1 covered, 1 missed => total 2
        ICounter lineInstr = mockCounter(0, 0);
        ILine line50 = mockLine(ICounter.PARTLY_COVERED, lineBranch, lineInstr);

        // Wire up
        IJavaModelCoverage modelCoverage = mock(IJavaModelCoverage.class);
        IJavaProject project = mock(IJavaProject.class);
        IType type = mock(IType.class);
        when(type.exists()).thenReturn(true);
        when(project.findType(className)).thenReturn(type);
        when(modelCoverage.getProjects()).thenReturn(new IJavaProject[]{project});

        SourceCoverageNode classCoverage = mock(SourceCoverageNode.class);
        when(modelCoverage.getCoverageFor(type)).thenReturn(classCoverage);
        when(classCoverage.getLineCounter()).thenReturn(classLine);
        when(classCoverage.getBranchCounter()).thenReturn(classBranch);
        when(classCoverage.getMethodCounter()).thenReturn(classMethod);

        when(classCoverage.getFirstLine()).thenReturn(50);
        when(classCoverage.getLastLine()).thenReturn(50);
        when(classCoverage.getLine(50)).thenReturn(line50);

        when(type.getMethods()).thenReturn(new IMethod[]{});

        try (MockedStatic<CoverageTools> mocked = mockStatic(CoverageTools.class)) {
            mocked.when(CoverageTools::getJavaModelCoverage).thenReturn(modelCoverage);

            JsonObject result = GSON.toJsonTree(CoverageHelper.getCoverageForClass(className)).getAsJsonObject();

            JsonArray lines = result.getAsJsonArray("lines");
            assertEquals(1, lines.size());
            JsonObject lineObj = lines.get(0).getAsJsonObject();
            assertEquals(50, lineObj.get("line").getAsInt());
            assertEquals("PARTLY_COVERED", lineObj.get("status").getAsString());
            assertEquals("1/2", lineObj.get("branches").getAsString());
        }
    }

    @Test
    void zeroCoveredFormatted() throws Exception {
        String className = "com.example.Empty";

        // Pre-create all counters
        ICounter zeroLine = mockCounter(0, 0);
        ICounter zeroBranch = mockCounter(0, 0);
        ICounter zeroMethod = mockCounter(0, 0);

        // Wire up
        IJavaModelCoverage modelCoverage = mock(IJavaModelCoverage.class);
        IJavaProject project = mock(IJavaProject.class);
        IType type = mock(IType.class);
        when(type.exists()).thenReturn(true);
        when(project.findType(className)).thenReturn(type);
        when(modelCoverage.getProjects()).thenReturn(new IJavaProject[]{project});

        SourceCoverageNode classCoverage = mock(SourceCoverageNode.class);
        when(modelCoverage.getCoverageFor(type)).thenReturn(classCoverage);
        when(classCoverage.getLineCounter()).thenReturn(zeroLine);
        when(classCoverage.getBranchCounter()).thenReturn(zeroBranch);
        when(classCoverage.getMethodCounter()).thenReturn(zeroMethod);
        when(classCoverage.getFirstLine()).thenReturn(ISourceNode.UNKNOWN_LINE);

        when(type.getMethods()).thenReturn(new IMethod[]{});

        try (MockedStatic<CoverageTools> mocked = mockStatic(CoverageTools.class)) {
            mocked.when(CoverageTools::getJavaModelCoverage).thenReturn(modelCoverage);

            JsonObject result = GSON.toJsonTree(CoverageHelper.getCoverageForClass(className)).getAsJsonObject();

            JsonObject summary = result.getAsJsonObject("summary");
            assertEquals("0/0", summary.get("lineCoverage").getAsString());
            assertEquals("0/0", summary.get("branchCoverage").getAsString());
            assertEquals("0/0", summary.get("methodCoverage").getAsString());
        }
    }
}
