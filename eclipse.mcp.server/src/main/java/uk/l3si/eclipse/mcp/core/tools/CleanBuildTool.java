package uk.l3si.eclipse.mcp.core.tools;

import uk.l3si.eclipse.mcp.model.CleanBuildResult;
import uk.l3si.eclipse.mcp.model.ProblemsResult;
import uk.l3si.eclipse.mcp.tools.Args;
import uk.l3si.eclipse.mcp.tools.McpTool;
import uk.l3si.eclipse.mcp.tools.ProgressReporter;
import uk.l3si.eclipse.mcp.tools.InputSchema;
import uk.l3si.eclipse.mcp.tools.PropertySchema;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;

import java.util.List;

public class CleanBuildTool implements McpTool {

    @Override
    public String getName() {
        return "clean_build";
    }

    @Override
    public String getDescription() {
        return "Clean and rebuild Eclipse projects from scratch. Use when Eclipse gets heavily out of sync (stale errors, missing classes, broken incremental build state). Performs a full clean followed by a complete rebuild. Returns any compilation errors found after the build.";
    }

    @Override
    public InputSchema getInputSchema() {
        return InputSchema.builder()
                .property("projects", PropertySchema.array("Optional: list of project names to clean/rebuild. If omitted, cleans and rebuilds all open projects.", PropertySchema.string("project name")))
                .build();
    }

    @Override
    public Object execute(Args args, ProgressReporter progress) throws Exception {
        List<String> projectNames = args.getStringList("projects");
        List<String> builtProjects = ProjectBuilder.cleanAndBuild(projectNames, progress);

        IMarker[] markers = ResourcesPlugin.getWorkspace().getRoot()
                .findMarkers(GetProblemsTool.JAVA_PROBLEM_MARKER, true, IResource.DEPTH_INFINITE);
        ProblemsResult problems = GetProblemsTool.collectProblems(markers, true);

        return CleanBuildResult.builder()
                .projects(builtProjects)
                .problems(problems)
                .build();
    }
}
