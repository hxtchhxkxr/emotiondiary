package com.example.emotiondiary.sample;

import com.example.emotiondiary.entity.Diary;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumingThat;

@DisplayName("Diary 도메인")
class DiaryDomainTest {

    @Nested
    @DisplayName("일기 작성 시")
    class WriteDiary {

        @Test
        @DisplayName("내용과 감정이 정상 저장된다")
        void write_success() { /* ... */ }

        @Test
        @DisplayName("내용이 2000자를 초과하면 예외가 발생한다")
        void write_tooLong_throws() { /* ... */ }

        @Test
        @DisplayName("Diary 생성 결과를 한 번에 검증")
        void diaryCreation_allFields() {
            Diary d = Diary.builder()
                    .date(1_700_000_000_000L)
                    .content("좋은 하루")
                    .emotionId(5)
                    .build();

            assertAll("Diary 필드",
                    () -> assertEquals(5, d.getEmotionId()),
                    () -> assertEquals("좋은 하루", d.getContent()),
                    () -> assertNotNull(d.getDate())
            );
        }
    }

    @Nested
    @DisplayName("일기 수정 시")
    class UpdateDiary {

        @Test
        @DisplayName("작성자 본인이면 수정에 성공한다")
        void update_byOwner_success() { /* ... */ }

        @Test
        @DisplayName("타인의 일기는 수정할 수 없다")
        void update_byOther_forbidden() { /* ... */ }
    }

    @Nested
    @DisplayName("AssertJ 사용법")
    class AssertJLearning {

        @Test
        @DisplayName("감정 목록을 AssertJ로 검증")
        void emotionList_assertJ() {
            List<String> emotions =
                    List.of("happy", "sad", "angry", "calm", "excited");

            assertThat(emotions)
                    .hasSize(5)
                    .contains("happy", "calm")
                    .doesNotContain("bored")
                    .startsWith("happy")
                    .endsWith("excited");
        }

        @Test
        @DisplayName("Diary 필드를 추출해서 검증")
        void diary_extracting() {
            Diary diary = Diary.builder()
                    .date(100L)
                    .content("hello")
                    .emotionId(3)
                    .build();

            assertThat(diary)
                    .extracting("content", "emotionId")
                    .containsExactly("hello", 3);
        }

        @Test
        @DisplayName("발생한 예외를 체이닝으로 검증")
        void exception_assertJ() {
            assertThatThrownBy(() -> Integer.parseInt("x"))
                    .isInstanceOf(NumberFormatException.class)
                    .hasMessageContaining("x");
        }
    }

    @Nested
    @DisplayName("JUnit Assumptions 사용법")
    class AssumptionsLearning {

        @Test
        @DisplayName("prod 환경이 아닐 때만 실행")
        void onlyOnNonProd() {
            assumeFalse("prod".equals(System.getenv("PROFILE")));

            assertThat(1 + 1).isEqualTo(2);
        }

        @Test
        @DisplayName("Windows에서만 추가 검증")
        void conditionally() {
            String osName = System.getProperty("os.name").toLowerCase();

            assertThat(osName).isNotBlank();

            assumingThat(osName.contains("win"), () ->
                    assertThat(osName).contains("win")
            );
        }
    }

    @Nested
    @DisplayName("매개변수화 테스트 사용법")
    class ParameterizedTests {

        @ParameterizedTest(name = "[{index}] emotionId={0}은 유효 범위이다")
        @ValueSource(ints = {1, 2, 3, 4, 5})
        @DisplayName("emotionId 유효 범위")
        void emotionId_validRange(int emotionId) {
            assertThat(emotionId).isBetween(1, 5);
        }

        @ParameterizedTest(name = "{0} + {1} = {2}")
        @CsvSource({
                "1, 2, 3",
                "5, 5, 10",
                "10, -5, 5",
                "0, 0, 0"
        })
        @DisplayName("덧셈 계산")
        void addition(int a, int b, int expected) {
            assertThat(a + b).isEqualTo(expected);
        }

        @ParameterizedTest(name = "{2}")
        @MethodSource("invalidDiaryInputs")
        @DisplayName("유효하지 않은 Diary 입력은 예외")
        void invalidInputs_throw(String content, int emotionId, String label) {
            assertThatThrownBy(() -> validate(content, emotionId))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        static Stream<Arguments> invalidDiaryInputs() {
            return Stream.of(
                    Arguments.of(null, 3, "내용이 null"),
                    Arguments.of("", 3, "내용이 빈 문자열"),
                    Arguments.of("정상", 0, "emotionId 하한 미달"),
                    Arguments.of("정상", 6, "emotionId 상한 초과")
            );
        }

        static void validate(String content, int emotionId) {
            if (content == null || content.isBlank()) {
                throw new IllegalArgumentException("content blank");
            }

            if (emotionId < 1 || emotionId > 5) {
                throw new IllegalArgumentException("emotionId out of range");
            }
        }
    }

    @RepeatedTest(value = 5, name = "{displayName} — {currentRepetition}/{totalRepetitions}")
    @DisplayName("UUID 생성은 매번 고유하다")
    void uuid_unique(RepetitionInfo info) {
        String a = UUID.randomUUID().toString();
        String b = UUID.randomUUID().toString();
        assertThat(a).isNotEqualTo(b);
        System.out.printf("repeat %d/%d%n", info.getCurrentRepetition(), info.getTotalRepetitions());
    }

    @TestFactory
    @DisplayName("emotionId 1~5 는 모두 유효 범위")
    Stream<DynamicTest> validEmotionIds() {
        return IntStream.rangeClosed(1, 5)
                .mapToObj(id -> DynamicTest.dynamicTest(
                        "emotionId = " + id,
                        () -> assertThat(id).isBetween(1, 5)
                ));
    }

    @TestFactory
    @DisplayName("CRUD 시나리오 순서 보장")
    Collection<DynamicTest> crudScenario() {
        var state = new Object() { String savedId; };

        return List.of(
                DynamicTest.dynamicTest("생성", () -> state.savedId = "diary-123"),
                DynamicTest.dynamicTest("조회",  () -> assertThat(state.savedId).isNotNull()),
                DynamicTest.dynamicTest("수정",  () -> { /* ... */ }),
                DynamicTest.dynamicTest("삭제",  () -> state.savedId = null)
        );
    }

    @Test
    @Timeout(value = 500, unit = TimeUnit.MILLISECONDS)
    @DisplayName("일기 생성 로직은 500ms 이내에 끝나야 한다")
    void write_underTimeLimit() {
        // ... 로직이 오래 걸리면 실패
    }
}
