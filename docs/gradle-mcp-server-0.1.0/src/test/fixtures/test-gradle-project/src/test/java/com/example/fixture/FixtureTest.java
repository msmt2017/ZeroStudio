package com.example.fixture;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FixtureTest {

    @Test
    void rootPassingTest() {
        System.out.println("STDOUT from rootPassingTest");
        assertTrue(true, "Root test should pass");
    }

    @Test
    void rootFailingTest() {
        System.err.println("STDERR from rootFailingTest");
        fail("This root test is designed to fail.");
    }
}
