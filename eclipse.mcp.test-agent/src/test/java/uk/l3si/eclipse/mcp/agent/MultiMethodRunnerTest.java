package uk.l3si.eclipse.mcp.agent;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.runner.Description;
import org.junit.runner.Request;
import org.junit.runner.Runner;
import org.junit.runner.manipulation.Filter;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MultiMethodRunnerTest {

    // -- parseMethods ---------------------------------------------------------

    @Nested
    class ParseMethods {
        @Test
        void splitsByComma() {
            assertArrayEquals(new String[]{"testA", "testB", "testC"},
                    MultiMethodRunner.parseMethods("testA,testB,testC"));
        }

        @Test
        void trimsWhitespace() {
            assertArrayEquals(new String[]{"testA", "testB"},
                    MultiMethodRunner.parseMethods(" testA , testB "));
        }

        @Test
        void singleMethod() {
            assertArrayEquals(new String[]{"testOnly"},
                    MultiMethodRunner.parseMethods("testOnly"));
        }

        @Test
        void filtersEmptySegments() {
            assertArrayEquals(new String[]{"testA", "testB"},
                    MultiMethodRunner.parseMethods("testA,,testB, ,"));
        }

        @Test
        void nullReturnsEmpty() {
            assertArrayEquals(new String[0], MultiMethodRunner.parseMethods(null));
        }

        @Test
        void blankReturnsEmpty() {
            assertArrayEquals(new String[0], MultiMethodRunner.parseMethods("   "));
        }

        @Test
        void emptyStringReturnsEmpty() {
            assertArrayEquals(new String[0], MultiMethodRunner.parseMethods(""));
        }
    }

    // -- execute validation ---------------------------------------------------

    @Nested
    class ExecuteValidation {
        @AfterEach
        void clearProperty() {
            System.clearProperty(RunMethodTransformer.PROPERTY_NAME);
        }

        @Test
        void throwsOnNullRunner() {
            assertThrows(IllegalArgumentException.class,
                    () -> MultiMethodRunner.execute(null));
        }

        @Test
        void throwsWhenPropertyNotSet() {
            System.clearProperty(RunMethodTransformer.PROPERTY_NAME);
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> MultiMethodRunner.execute(new Object()));
            assertTrue(ex.getMessage().contains("empty or not set"));
        }

        @Test
        void throwsWhenPropertyIsBlank() {
            System.setProperty(RunMethodTransformer.PROPERTY_NAME, "   ");
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> MultiMethodRunner.execute(new Object()));
            assertTrue(ex.getMessage().contains("empty or not set"));
        }
    }

    // -- findMethod -----------------------------------------------------------

    @Nested
    class FindMethod {

        static class Parent {
            protected void parentMethod() {}
        }

        static class Child extends Parent {
            public void childMethod() {}
            private void privateMethod() {}
        }

        interface SomeInterface {
            void interfaceMethod();
        }

        static abstract class ImplementsInterface implements SomeInterface {}

        @Test
        void findsPublicMethod() {
            Method m = MultiMethodRunner.findMethod(Child.class, "childMethod");
            assertEquals("childMethod", m.getName());
        }

        @Test
        void findsInheritedMethod() {
            Method m = MultiMethodRunner.findMethod(Child.class, "parentMethod");
            assertEquals("parentMethod", m.getName());
        }

        @Test
        void findsPrivateMethod() {
            Method m = MultiMethodRunner.findMethod(Child.class, "privateMethod");
            assertEquals("privateMethod", m.getName());
            assertTrue(m.canAccess(new Child()));
        }

        @Test
        void findsMethodWithParams() {
            Method m = MultiMethodRunner.findMethod(String.class, "charAt", int.class);
            assertEquals("charAt", m.getName());
        }

        @Test
        void throwsForNonExistentMethod() {
            assertThrows(IllegalStateException.class,
                    () -> MultiMethodRunner.findMethod(Child.class, "noSuchMethod"));
        }

        @Test
        void findsInterfaceMethod() {
            Method m = MultiMethodRunner.findMethod(ImplementsInterface.class, "interfaceMethod");
            assertEquals("interfaceMethod", m.getName());
        }
    }

    // -- findField ------------------------------------------------------------

    @Nested
    class FindField {

        static class Base {
            private String baseField = "base";
        }

        static class Derived extends Base {
            public int derivedField = 42;
        }

        @Test
        void findsDirectField() {
            Field f = MultiMethodRunner.findField(Derived.class, "derivedField");
            assertEquals("derivedField", f.getName());
        }

        @Test
        void findsInheritedPrivateField() throws Exception {
            Derived d = new Derived();
            Field f = MultiMethodRunner.findField(Derived.class, "baseField");
            assertEquals("baseField", f.getName());
            assertEquals("base", f.get(d));
        }

        @Test
        void throwsForNonExistentField() {
            assertThrows(IllegalStateException.class,
                    () -> MultiMethodRunner.findField(Derived.class, "noSuchField"));
        }
    }

    // -- createMultiMethodFilter (ASM-generated JUnit 4 filter) ---------------

    @Nested
    class CreateMultiMethodFilter {

        @Test
        void filterAllowsMatchingMethods() throws Exception {
            Filter filter = createFilter("testAdd", "testSubtract");

            assertTrue(filter.shouldRun(methodDesc("testAdd")));
            assertTrue(filter.shouldRun(methodDesc("testSubtract")));
        }

        @Test
        void filterRejectsNonMatchingMethods() throws Exception {
            Filter filter = createFilter("testAdd");
            assertFalse(filter.shouldRun(methodDesc("testMultiply")));
        }

        @Test
        void filterKeepsSuiteNodes() throws Exception {
            Filter filter = createFilter("testAdd");
            Description suite = Description.createSuiteDescription("com.example.MathTest");
            assertTrue(filter.shouldRun(suite),
                    "Suite nodes (null method) must be kept for the class tree to appear");
        }

        @Test
        void filterDescribeReturnsNonEmpty() throws Exception {
            Filter filter = createFilter("testAdd");
            assertNotNull(filter.describe());
            assertFalse(filter.describe().isEmpty());
        }

        @Test
        void filterWorksWithMultipleMethods() throws Exception {
            Filter filter = createFilter("a", "b", "c");
            assertTrue(filter.shouldRun(methodDesc("a")));
            assertTrue(filter.shouldRun(methodDesc("b")));
            assertTrue(filter.shouldRun(methodDesc("c")));
            assertFalse(filter.shouldRun(methodDesc("d")));
        }

        @Test
        void filterIsCaseSensitive() throws Exception {
            Filter filter = createFilter("testAdd");
            assertFalse(filter.shouldRun(methodDesc("TestAdd")));
            assertFalse(filter.shouldRun(methodDesc("TESTADD")));
            assertTrue(filter.shouldRun(methodDesc("testAdd")));
        }

        @Test
        void filterHandlesEmptyMethodName() throws Exception {
            Filter filter = createFilter("testAdd");
            Description emptyMethod = Description.createTestDescription(
                    "com.example.MathTest", "");
            assertFalse(filter.shouldRun(emptyMethod));
        }

        @Test
        void canCreateMultipleFiltersIndependently() throws Exception {
            Filter filter1 = createFilter("methodA");
            Filter filter2 = createFilter("methodB");

            assertTrue(filter1.shouldRun(methodDesc("methodA")));
            assertFalse(filter1.shouldRun(methodDesc("methodB")));
            assertFalse(filter2.shouldRun(methodDesc("methodA")));
            assertTrue(filter2.shouldRun(methodDesc("methodB")));
        }

        private Filter createFilter(String... methods) throws Exception {
            return (Filter) MultiMethodRunner.createMultiMethodFilter(
                    getClass().getClassLoader(), Filter.class, methods);
        }

        private Description methodDesc(String methodName) {
            return Description.createTestDescription("com.example.Test", methodName);
        }
    }

    // -- JUnit 4 filter + Request integration ---------------------------------

    @Nested
    class JUnit4FilterIntegration {

        /** A real JUnit 4 test class — JUnit discovers methods via reflection */
        public static class SampleJUnit4Test {
            @org.junit.Test public void alpha() {}
            @org.junit.Test public void beta() {}
            @org.junit.Test public void gamma() {}
            @org.junit.Test public void delta() {}
        }

        @Test
        void filterKeepsOnlySelectedMethods() throws Exception {
            Filter filter = (Filter) MultiMethodRunner.createMultiMethodFilter(
                    getClass().getClassLoader(), Filter.class,
                    new String[]{"alpha", "gamma"});

            Request request = Request.classWithoutSuiteMethod(SampleJUnit4Test.class);
            Request filtered = request.filterWith(filter);
            Runner runner = filtered.getRunner();

            List<String> methods = collectMethodNames(runner.getDescription());
            assertEquals(2, methods.size());
            assertTrue(methods.contains("alpha"));
            assertTrue(methods.contains("gamma"));
            assertFalse(methods.contains("beta"));
            assertFalse(methods.contains("delta"));
        }

        @Test
        void filterWithSingleMethod() throws Exception {
            Filter filter = (Filter) MultiMethodRunner.createMultiMethodFilter(
                    getClass().getClassLoader(), Filter.class,
                    new String[]{"beta"});

            Request request = Request.classWithoutSuiteMethod(SampleJUnit4Test.class);
            Request filtered = request.filterWith(filter);
            Runner runner = filtered.getRunner();

            List<String> methods = collectMethodNames(runner.getDescription());
            assertEquals(1, methods.size());
            assertEquals("beta", methods.get(0));
        }

        @Test
        void filterWithAllMethods() throws Exception {
            Filter filter = (Filter) MultiMethodRunner.createMultiMethodFilter(
                    getClass().getClassLoader(), Filter.class,
                    new String[]{"alpha", "beta", "gamma", "delta"});

            Request request = Request.classWithoutSuiteMethod(SampleJUnit4Test.class);
            Request filtered = request.filterWith(filter);
            Runner runner = filtered.getRunner();

            List<String> methods = collectMethodNames(runner.getDescription());
            assertEquals(4, methods.size());
        }

        @Test
        void filteredRunnerDescription_hasClassAsRoot() throws Exception {
            Filter filter = (Filter) MultiMethodRunner.createMultiMethodFilter(
                    getClass().getClassLoader(), Filter.class,
                    new String[]{"alpha", "beta"});

            Request request = Request.classWithoutSuiteMethod(SampleJUnit4Test.class);
            Runner runner = request.filterWith(filter).getRunner();
            Description root = runner.getDescription();

            // Root should be the class, children should be the methods
            assertTrue(root.isSuite());
            assertEquals(2, root.getChildren().size());
        }

        private List<String> collectMethodNames(Description description) {
            List<String> names = new ArrayList<>();
            for (Description child : description.getChildren()) {
                if (child.isTest()) {
                    names.add(child.getMethodName());
                } else {
                    names.addAll(collectMethodNames(child));
                }
            }
            return names;
        }
    }

    // -- JUnit 5 Platform request building ------------------------------------

    @Nested
    class JUnit5PlatformRequestBuilding {

        @Test
        void selectMethodCreatesValidSelector() throws Exception {
            var selector = org.junit.platform.engine.discovery.DiscoverySelectors
                    .selectMethod("com.example.MyTest#testFoo");
            assertNotNull(selector);
            assertEquals("com.example.MyTest", selector.getClassName());
            assertEquals("testFoo", selector.getMethodName());
        }

        @Test
        void multiSelectorRequestBuilds() throws Exception {
            var request = org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder
                    .request()
                    .selectors(
                            org.junit.platform.engine.discovery.DiscoverySelectors.selectMethod("com.example.T#a"),
                            org.junit.platform.engine.discovery.DiscoverySelectors.selectMethod("com.example.T#b"),
                            org.junit.platform.engine.discovery.DiscoverySelectors.selectMethod("com.example.T#c")
                    )
                    .build();
            assertNotNull(request);
            // Verify the request contains all 3 selectors
            var selectors = request.getSelectorsByType(
                    org.junit.platform.engine.discovery.MethodSelector.class);
            assertEquals(3, selectors.size());
            assertEquals("a", selectors.get(0).getMethodName());
            assertEquals("b", selectors.get(1).getMethodName());
            assertEquals("c", selectors.get(2).getMethodName());
        }

        @Test
        void selectorFormatMatchesAgentUsage() {
            // The agent builds "className#methodName" — verify this format works
            String className = "uk.l3si.maestro.physics.sim.MathUtilTest";
            String methodName = "sinZeroIsZero";
            var selector = org.junit.platform.engine.discovery.DiscoverySelectors
                    .selectMethod(className + "#" + methodName);
            assertEquals(className, selector.getClassName());
            assertEquals(methodName, selector.getMethodName());
        }
    }

    // -- findLoadTestsMethod --------------------------------------------------

    @Nested
    class FindLoadTestsMethod {

        /** Fake loader interface mimicking Eclipse's ITestLoader */
        interface FakeTestLoader {
            Object[] loadTests(Class<?>[] classes, String testName,
                    String[] failureNames, String[] packageNames,
                    String[][] tags, String uniqueId, Object runner);
        }

        static class FakeLoaderImpl implements FakeTestLoader {
            @Override
            public Object[] loadTests(Class<?>[] classes, String testName,
                    String[] failureNames, String[] packageNames,
                    String[][] tags, String uniqueId, Object runner) {
                return new Object[0];
            }
        }

        @Test
        void findsMethodOnImplementation() throws Exception {
            // Use reflection to access the private findLoadTestsMethod
            Method findMethod = MultiMethodRunner.class.getDeclaredMethod(
                    "findLoadTestsMethod", Class.class, Class.class);
            findMethod.setAccessible(true);

            Method result = (Method) findMethod.invoke(null,
                    FakeLoaderImpl.class, Object.class);
            assertNotNull(result);
            assertEquals("loadTests", result.getName());
            assertEquals(7, result.getParameterCount());
        }

        @Test
        void findsMethodViaInterface() throws Exception {
            Method findMethod = MultiMethodRunner.class.getDeclaredMethod(
                    "findLoadTestsMethod", Class.class, Class.class);
            findMethod.setAccessible(true);

            // FakeLoaderImpl implements FakeTestLoader which has loadTests
            Method result = (Method) findMethod.invoke(null,
                    FakeLoaderImpl.class, Object.class);
            assertNotNull(result);
        }

        @Test
        void throwsWhenMethodNotFound() throws Exception {
            Method findMethod = MultiMethodRunner.class.getDeclaredMethod(
                    "findLoadTestsMethod", Class.class, Class.class);
            findMethod.setAccessible(true);

            assertThrows(Exception.class, () ->
                    findMethod.invoke(null, String.class, Object.class));
        }
    }

    // -- RunMethodTransformer -------------------------------------------------

    @Nested
    class Transformer {
        @Test
        void propertyNameConstantIsCorrect() {
            assertEquals("eclipse.mcp.test.methods",
                    RunMethodTransformer.PROPERTY_NAME);
        }
    }
}
