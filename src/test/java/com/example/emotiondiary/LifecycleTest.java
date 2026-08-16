package com.example.emotiondiary;

import org.junit.jupiter.api.*;

@DisplayName("JUnit 5 생명주기 학습")
class LifecycleTest {

    @BeforeAll
    static void beforeAll() { System.out.println("BeforeAll — 1회"); }

    @BeforeEach
    void beforeEach() { System.out.println("  BeforeEach"); }

    @Test
    @DisplayName("첫 번째 테스트")
    void test1() { System.out.println("    test1 실행"); }

    @Test
    @DisplayName("두 번째 테스트")
    void test2() { System.out.println("    test2 실행"); }

    @AfterEach
    void afterEach() { System.out.println("  AfterEach"); }

    @AfterAll
    static void afterAll() { System.out.println("AfterAll — 1회"); }
}