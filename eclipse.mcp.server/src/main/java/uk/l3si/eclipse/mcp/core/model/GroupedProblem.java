package uk.l3si.eclipse.mcp.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.Builder;
import lombok.Getter;

/**
 * Groups compilation errors by project+message to reduce token usage.
 * Instead of repeating "Foo cannot be resolved" 30 times across 30 files,
 * produces one entry with count=30 and a few example locations.
 */
@Builder
@Getter
public class GroupedProblem {
    private String project;
    private String message;
    private int count;
    private List<ProblemInfo> locations;

    private static final int MAX_LOCATIONS = 3;
    private static final int MAX_GROUPS = 10;

    public static List<GroupedProblem> group(List<ProblemInfo> problems) {
        return group(problems, MAX_GROUPS);
    }

    public static List<GroupedProblem> group(List<ProblemInfo> problems, int maxGroups) {
        // Group by (project, message) so project appears once per group
        Map<String, List<ProblemInfo>> byKey = new LinkedHashMap<>();
        for (ProblemInfo p : problems) {
            String key = p.getProject() + "\0" + p.getMessage();
            byKey.computeIfAbsent(key, k -> new ArrayList<>()).add(p);
        }

        List<GroupedProblem> result = new ArrayList<>();
        for (List<ProblemInfo> group : byKey.values()) {
            ProblemInfo first = group.get(0);
            List<ProblemInfo> locations = new ArrayList<>();
            for (int i = 0; i < Math.min(group.size(), MAX_LOCATIONS); i++) {
                ProblemInfo p = group.get(i);
                // Only file+line in locations — project and message are on the group
                locations.add(ProblemInfo.builder()
                        .file(p.getFile())
                        .line(p.getLine())
                        .build());
            }
            result.add(GroupedProblem.builder()
                    .project(first.getProject())
                    .message(first.getMessage())
                    .count(group.size())
                    .locations(locations)
                    .build());
        }

        // Most frequent errors first
        result.sort((a, b) -> Integer.compare(b.count, a.count));

        if (result.size() > maxGroups) {
            return result.subList(0, maxGroups);
        }
        return result;
    }

    public static String summarize(List<ProblemInfo> problems) {
        if (problems.isEmpty()) {
            return null;
        }
        int total = problems.size();

        // Count unique messages and unique projects
        Map<String, Integer> byKey = new LinkedHashMap<>();
        for (ProblemInfo p : problems) {
            String key = p.getProject() + "\0" + p.getMessage();
            byKey.merge(key, 1, Integer::sum);
        }
        int unique = byKey.size();

        long projectCount = problems.stream()
                .map(ProblemInfo::getProject)
                .distinct()
                .count();

        String projectsPart = projectCount == 1
                ? "in project '" + problems.get(0).getProject() + "'"
                : "across " + projectCount + " projects";

        if (unique == total) {
            return total + " errors " + projectsPart;
        }
        return total + " errors (" + unique + " unique) " + projectsPart;
    }
}
