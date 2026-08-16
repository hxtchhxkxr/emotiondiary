package com.example.emotiondiary.sample;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("@Mock vs @Spy 기본 동작")
@ExtendWith(MockitoExtension.class)
class MockVsSpyTest {

    static class Greeter {
        String hello() {
            System.out.println("hello메서드 실행");
            return "안녕하세요, 홍길동입니다.";
        }
    }

    @Mock
    Greeter mocked;   // 메서드가 전부 null/0/false 를 리턴

    @Spy
    Greeter spied = new Greeter();   // 실제 메서드가 실행됨

    @Test
    @DisplayName("@Mock 은 기본 반환값(null)을 돌려준다")
    void mock_returnsDefault() {
        assertThat(mocked.hello()).isNull(); // 실제로는 hello 메서드 호출안됨
    }

    @Test
    @DisplayName("@Spy 는 진짜 메서드가 실행된다")
    void spy_runsRealMethod() {
        assertThat(spied.hello()).isEqualTo("안녕하세요, 홍길동입니다.");
    }
}