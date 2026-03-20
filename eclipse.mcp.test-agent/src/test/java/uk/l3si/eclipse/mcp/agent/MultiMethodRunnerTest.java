package uk.l3si.eclipse.mcp.agent;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.runner.Description;
import org.junit.runner.manipulation.Filter;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
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

            Description testAdd = Description.createTestDescription(
                    "com.example.MathTest", "testAdd");
            Description testSubtract = Description.createTestDescription(
                    "com.example.MathTest", "testSubtract");

            assertTrue(filter.shouldRun(testAdd));
            assertTrue(filter.shouldRun(testSubtract));
        }

        @Test
        void filterRejectsNonMatchingMethods() throws Exception {
            Filter filter = createFilter("testAdd");

            Description testMultiply = Description.createTestDescription(
                    "com.example.MathTest", "testMultiply");

            assertFalse(filter.shouldRun(testMultiply));
        }

        @Test
        void filterKeepsSuiteNodes() throws Exception {
            Filter filter = createFilter("testAdd");

            // Suite descriptions have null method name
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

            assertTrue(filter.shouldRun(desc("a")));
            assertTrue(filter.shouldRun(desc("b")));
            assertTrue(filter.shouldRun(desc("c")));
            assertFalse(filter.shouldRun(desc("d")));
            assertFalse(filter.shouldRun(desc("e")));
        }

        @Test
        void filterIsCaseSensitive() throws Exception {
            Filter filter = createFilter("testAdd");

            assertFalse(filter.shouldRun(desc("TestAdd")));
            assertFalse(filter.shouldRun(desc("TESTADD")));
            assertTrue(filter.shouldRun(desc("testAdd")));
        }

        @Test
        void filterHandlesEmptyMethodName() throws Exception {
            // Description with empty string method name (edge case)
            Filter filter = createFilter("testAdd");
            Description emptyMethod = Description.createTestDescription(
                    "com.example.MathTest", "");
            // Empty string is not null, so it goes through contains() check
            assertFalse(filter.shouldRun(emptyMethod));
        }

        @Test
        void canCreateMultipleFiltersIndependently() throws Exception {
            Filter filter1 = createFilter("methodA");
            Filter filter2 = createFilter("methodB");

            assertTrue(filter1.shouldRun(desc("methodA")));
            assertFalse(filter1.shouldRun(desc("methodB")));

            assertFalse(filter2.shouldRun(desc("methodA")));
            assertTrue(filter2.shouldRun(desc("methodB")));
        }

        private Filter createFilter(String... methods) throws Exception {
            return (Filter) MultiMethodRunner.createMultiMethodFilter(
                    getClass().getClassLoader(), Filter.class, methods);
        }

        private Description desc(String methodName) {
            return Description.createTestDescription("com.example.Test", methodName);
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
