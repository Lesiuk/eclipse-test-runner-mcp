package uk.l3si.eclipse.mcp.core.tools;

import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestLaunchHelperTest {

    @Test
    void validMethodDoesNotThrow() throws Exception {
        IType type = mock(IType.class);
        IMethod method = mock(IMethod.class);
        when(method.getElementName()).thenReturn("testAdd");
        when(type.getMethods()).thenReturn(new IMethod[]{method});

        assertDoesNotThrow(() ->
                TestLaunchHelper.validateMethodOnType(type, "com.test.FooTest", "testAdd"));
    }

    @Test
    void invalidMethodThrowsWithAvailableMethods() throws Exception {
        IType type = mock(IType.class);
        IMethod method1 = mock(IMethod.class);
        when(method1.getElementName()).thenReturn("testAdd");
        IMethod method2 = mock(IMethod.class);
        when(method2.getElementName()).thenReturn("testSubtract");
        when(type.getMethods()).thenReturn(new IMethod[]{method1, method2});

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                TestLaunchHelper.validateMethodOnType(type, "com.test.FooTest", "nonExistent"));
        assertTrue(ex.getMessage().contains("nonExistent"));
        assertTrue(ex.getMessage().contains("not found"));
        assertTrue(ex.getMessage().contains("testAdd"));
        assertTrue(ex.getMessage().contains("testSubtract"));
    }

    @Test
    void noMethodsInClassShowsEmptyList() throws Exception {
        IType type = mock(IType.class);
        when(type.getMethods()).thenReturn(new IMethod[]{});

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                TestLaunchHelper.validateMethodOnType(type, "com.test.EmptyTest", "testSomething"));
        assertTrue(ex.getMessage().contains("testSomething"));
        assertTrue(ex.getMessage().contains("not found"));
    }

    @Test
    void matchesExactMethodNameOnly() throws Exception {
        IType type = mock(IType.class);
        IMethod method = mock(IMethod.class);
        when(method.getElementName()).thenReturn("testAddition");
        when(type.getMethods()).thenReturn(new IMethod[]{method});

        // "testAdd" should NOT match "testAddition"
        assertThrows(IllegalArgumentException.class, () ->
                TestLaunchHelper.validateMethodOnType(type, "com.test.FooTest", "testAdd"));

        // exact match should work
        assertDoesNotThrow(() ->
                TestLaunchHelper.validateMethodOnType(type, "com.test.FooTest", "testAddition"));
    }
}
