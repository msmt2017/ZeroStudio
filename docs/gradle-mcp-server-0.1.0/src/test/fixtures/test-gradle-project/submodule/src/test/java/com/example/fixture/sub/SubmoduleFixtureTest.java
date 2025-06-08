package com.example.fixture.sub;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled; // For skipped test
import static org.junit.jupiter.api.Assertions.*;

class SubmoduleFixtureTest {

    @Test
    void subPassingTest() {
        System.out.println("STDOUT from subPassingTest");
        assertTrue(true, "Submodule test should pass");
    }

    @Disabled("This test is skipped for demonstration")
    @Test
    void subSkippedTest() {
        fail("This should not run");
    }
}
