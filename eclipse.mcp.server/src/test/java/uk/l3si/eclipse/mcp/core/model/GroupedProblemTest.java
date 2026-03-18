package uk.l3si.eclipse.mcp.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GroupedProblemTest {

    // --- group() tests ---

    @Test
    void emptyListReturnsEmpty() {
        List<GroupedProblem> result = GroupedProblem.group(List.of());
        assertTrue(result.isEmpty());
    }

    @Test
    void singleErrorProducesSingleGroup() {
        List<ProblemInfo> input = List.of(
                problem("proj", "Foo.java", 10, "Foo cannot be resolved"));

        List<GroupedProblem> result = GroupedProblem.group(input);

        assertEquals(1, result.size());
        GroupedProblem g = result.get(0);
        assertEquals("proj", g.getProject());
        assertEquals("Foo cannot be resolved", g.getMessage());
        assertEquals(1, g.getCount());
        assertEquals(1, g.getLocations().size());
        assertEquals("Foo.java", g.getLocations().get(0).getFile());
        assertEquals(10, g.getLocations().get(0).getLine());
    }

    @Test
    void duplicateMessagesGroupedByProjectAndMessage() {
        List<ProblemInfo> input = List.of(
                problem("proj", "A.java", 1, "Foo cannot be resolved"),
                problem("proj", "B.java", 2, "Foo cannot be resolved"),
                problem("proj", "C.java", 3, "Foo cannot be resolved"));

        List<GroupedProblem> result = GroupedProblem.group(input);

        assertEquals(1, result.size());
        assertEquals("Foo cannot be resolved", result.get(0).getMessage());
        assertEquals(3, result.get(0).getCount());
    }

    @Test
    void locationsCappedAtThree() {
        List<ProblemInfo> input = List.of(
                problem("proj", "A.java", 1, "missing type"),
                problem("proj", "B.java", 2, "missing type"),
                problem("proj", "C.java", 3, "missing type"),
                problem("proj", "D.java", 4, "missing type"),
                problem("proj", "E.java", 5, "missing type"));

        List<GroupedProblem> result = GroupedProblem.group(input);

        assertEquals(1, result.size());
        assertEquals(5, result.get(0).getCount());
        assertEquals(3, result.get(0).getLocations().size());
        assertEquals("A.java", result.get(0).getLocations().get(0).getFile());
        assertEquals("B.java", result.get(0).getLocations().get(1).getFile());
        assertEquals("C.java", result.get(0).getLocations().get(2).getFile());
    }

    @Test
    void differentMessagesProduceSeparateGroups() {
        List<ProblemInfo> input = List.of(
                problem("proj", "A.java", 1, "Foo cannot be resolved"),
                problem("proj", "B.java", 2, "Bar cannot be resolved"));

        List<GroupedProblem> result = GroupedProblem.group(input);

        assertEquals(2, result.size());
        List<String> messages = result.stream().map(GroupedProblem::getMessage).toList();
        assertTrue(messages.contains("Foo cannot be resolved"));
        assertTrue(messages.contains("Bar cannot be resolved"));
    }

    @Test
    void sameMessageDifferentProjectsProduceSeparateGroups() {
        List<ProblemInfo> input = List.of(
                problem("alpha", "A.java", 1, "missing type"),
                problem("alpha", "B.java", 2, "missing type"),
                problem("beta", "C.java", 3, "missing type"));

        List<GroupedProblem> result = GroupedProblem.group(input);

        assertEquals(2, result.size());
        GroupedProblem alphaGroup = result.stream()
                .filter(g -> "alpha".equals(g.getProject())).findFirst().orElseThrow();
        GroupedProblem betaGroup = result.stream()
                .filter(g -> "beta".equals(g.getProject())).findFirst().orElseThrow();
        assertEquals(2, alphaGroup.getCount());
        assertEquals(1, betaGroup.getCount());
    }

    @Test
    void sortedByCountDescending() {
        List<ProblemInfo> input = List.of(
                problem("proj", "A.java", 1, "rare error"),
                problem("proj", "B.java", 2, "common error"),
                problem("proj", "C.java", 3, "common error"),
                problem("proj", "D.java", 4, "common error"),
                problem("proj", "E.java", 5, "medium error"),
                problem("proj", "F.java", 6, "medium error"));

        List<GroupedProblem> result = GroupedProblem.group(input);

        assertEquals(3, result.size());
        assertEquals("common error", result.get(0).getMessage());
        assertEquals(3, result.get(0).getCount());
        assertEquals("medium error", result.get(1).getMessage());
        assertEquals(2, result.get(1).getCount());
        assertEquals("rare error", result.get(2).getMessage());
        assertEquals(1, result.get(2).getCount());
    }

    @Test
    void locationsOmitProjectAndMessage() {
        List<ProblemInfo> input = List.of(
                problem("proj", "A.java", 10, "some error"));

        List<GroupedProblem> result = GroupedProblem.group(input);

        ProblemInfo loc = result.get(0).getLocations().get(0);
        assertEquals("A.java", loc.getFile());
        assertEquals(10, loc.getLine());
        assertNull(loc.getProject(), "project should not be on location");
        assertNull(loc.getMessage(), "message should not be on location");
    }

    @Test
    void nullLinePreservedInLocation() {
        List<ProblemInfo> input = List.of(
                ProblemInfo.builder()
                        .project("proj").file("A.java").message("error").build());

        List<GroupedProblem> result = GroupedProblem.group(input);

        assertNull(result.get(0).getLocations().get(0).getLine());
    }

    @Test
    void groupsCappedAtDefault() {
        // Create 15 unique errors — should be capped to 10 by default
        List<ProblemInfo> input = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            input.add(problem("proj", "File" + i + ".java", i, "error " + i));
        }

        List<GroupedProblem> result = GroupedProblem.group(input);

        assertEquals(10, result.size());
    }

    @Test
    void groupsCappedAtCustomLimit() {
        List<ProblemInfo> input = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            input.add(problem("proj", "File" + i + ".java", i, "error " + i));
        }

        List<GroupedProblem> result = GroupedProblem.group(input, 5);

        assertEquals(5, result.size());
    }

    @Test
    void capKeepsMostFrequentGroups() {
        List<ProblemInfo> input = new ArrayList<>();
        // 1 rare error
        input.add(problem("proj", "Rare.java", 1, "rare"));
        // 5 common errors
        for (int i = 0; i < 5; i++) {
            input.add(problem("proj", "F" + i + ".java", i, "common"));
        }
        // 8 more unique rare errors to push total unique count above cap
        for (int i = 0; i < 8; i++) {
            input.add(problem("proj", "X" + i + ".java", i, "unique " + i));
        }

        List<GroupedProblem> result = GroupedProblem.group(input, 3);

        assertEquals(3, result.size());
        // Most frequent group (common, count=5) must be first
        assertEquals("common", result.get(0).getMessage());
        assertEquals(5, result.get(0).getCount());
    }

    @Test
    void fewerGroupsThanCapReturnsAll() {
        List<ProblemInfo> input = List.of(
                problem("proj", "A.java", 1, "error A"),
                problem("proj", "B.java", 2, "error B"));

        List<GroupedProblem> result = GroupedProblem.group(input);

        assertEquals(2, result.size());
    }

    @Test
    void realWorldScenarioManyDuplicates() {
        List<ProblemInfo> input = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            input.add(problem("backend", "File" + i + ".java", i + 1,
                    "BdoKeoEntryResponse cannot be resolved to a type"));
        }
        for (int i = 0; i < 15; i++) {
            input.add(problem("backend", "File" + i + ".java", i + 100,
                    "BdoKeoClient cannot be resolved to a type"));
        }
        input.add(problem("backend", "Unique.java", 1, "syntax error on token"));

        List<GroupedProblem> result = GroupedProblem.group(input);

        assertEquals(3, result.size());
        assertEquals(30, result.get(0).getCount());
        assertEquals("BdoKeoEntryResponse cannot be resolved to a type", result.get(0).getMessage());
        assertEquals(3, result.get(0).getLocations().size(), "locations capped at 3");
        assertEquals(15, result.get(1).getCount());
        assertEquals(1, result.get(2).getCount());
    }

    // --- summarize() tests ---

    @Test
    void summarizeEmptyReturnsNull() {
        assertNull(GroupedProblem.summarize(List.of()));
    }

    @Test
    void summarizeSingleError() {
        List<ProblemInfo> input = List.of(
                problem("backend", "A.java", 1, "some error"));

        assertEquals("1 errors in project 'backend'", GroupedProblem.summarize(input));
    }

    @Test
    void summarizeAllUnique() {
        List<ProblemInfo> input = List.of(
                problem("proj", "A.java", 1, "error A"),
                problem("proj", "B.java", 2, "error B"),
                problem("proj", "C.java", 3, "error C"));

        // All unique — no "(N unique)" qualifier needed
        assertEquals("3 errors in project 'proj'", GroupedProblem.summarize(input));
    }

    @Test
    void summarizeDuplicatesShowsUniqueCount() {
        List<ProblemInfo> input = List.of(
                problem("proj", "A.java", 1, "missing type"),
                problem("proj", "B.java", 2, "missing type"),
                problem("proj", "C.java", 3, "other error"));

        assertEquals("3 errors (2 unique) in project 'proj'", GroupedProblem.summarize(input));
    }

    @Test
    void summarizeMultipleProjects() {
        List<ProblemInfo> input = List.of(
                problem("alpha", "A.java", 1, "err"),
                problem("alpha", "B.java", 2, "err"),
                problem("beta", "C.java", 3, "err"));

        // 3 total, 2 unique (project,message) pairs: (alpha,err) and (beta,err)
        assertEquals("3 errors (2 unique) across 2 projects", GroupedProblem.summarize(input));
    }

    @Test
    void summarizeMultipleProjectsAllUnique() {
        List<ProblemInfo> input = List.of(
                problem("alpha", "A.java", 1, "error A"),
                problem("beta", "B.java", 2, "error B"));

        assertEquals("2 errors across 2 projects", GroupedProblem.summarize(input));
    }

    @Test
    void summarizeRealWorldScale() {
        List<ProblemInfo> input = new ArrayList<>();
        // 150 unique error messages, many duplicated across files
        for (int msg = 0; msg < 150; msg++) {
            int dupes = (msg < 10) ? 20 : 3; // first 10 messages have 20 dupes each
            for (int d = 0; d < dupes; d++) {
                input.add(problem("backend", "File" + d + ".java", d, "error " + msg));
            }
        }

        String summary = GroupedProblem.summarize(input);

        // 10*20 + 140*3 = 200 + 420 = 620 total, 150 unique
        assertEquals("620 errors (150 unique) in project 'backend'", summary);
    }

    private static ProblemInfo problem(String project, String file, int line, String message) {
        return ProblemInfo.builder()
                .project(project).file(file).line(line).message(message)
                .build();
    }
}
