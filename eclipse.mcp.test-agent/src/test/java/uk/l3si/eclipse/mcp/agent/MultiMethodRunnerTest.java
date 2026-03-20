package uk.l3si.eclipse.mcp.agent;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class MultiMethodRunnerTest {

    @Test
    void parseMethods_splitsByComma() {
        String[] result = MultiMethodRunner.parseMethods("testA,testB,testC");
        assertArrayEquals(new String[]{"testA", "testB", "testC"}, result);
    }

    @Test
    void parseMethods_trimWhitespace() {
        String[] result = MultiMethodRunner.parseMethods(" testA , testB ");
        assertArrayEquals(new String[]{"testA", "testB"}, result);
    }

    @Test
    void parseMethods_singleMethod() {
        String[] result = MultiMethodRunner.parseMethods("testOnly");
        assertArrayEquals(new String[]{"testOnly"}, result);
    }

    @Test
    void parseMethods_filtersEmpty() {
        String[] result = MultiMethodRunner.parseMethods("testA,,testB, ,");
        assertArrayEquals(new String[]{"testA", "testB"}, result);
    }

    @Test
    void execute_throwsOnNullRunner() {
        assertThrows(IllegalArgumentException.class,
                () -> MultiMethodRunner.execute(null));
    }
}
