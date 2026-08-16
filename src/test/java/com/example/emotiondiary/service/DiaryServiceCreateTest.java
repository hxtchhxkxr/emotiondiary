package com.example.emotiondiary.service;

import com.example.emotiondiary.dto.DiaryRequest;
import com.example.emotiondiary.dto.DiaryResponse;
import com.example.emotiondiary.entity.Diary;
import com.example.emotiondiary.entity.User;
import com.example.emotiondiary.repository.DiaryRepository;
import com.example.emotiondiary.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("DiaryService.create() 단위 테스트")
@ExtendWith(MockitoExtension.class)
class DiaryServiceCreateTest {

    @Mock DiaryRepository diaryRepository;
    @Mock UserRepository userRepository;

    @InjectMocks DiaryService diaryService;

    @Test
    @DisplayName("정상 생성: User 를 찾아 Diary 를 저장하고 응답 DTO 를 반환한다")
    void create_success() throws Exception {
        // given
        User user = mockUser(1L);
        DiaryRequest req = newRequest(1_700_000_000_000L, "좋은 하루", 5);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        // save() 에 들어온 엔티티를 그대로 돌려주도록 stubbing
        when(diaryRepository.save(any(Diary.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // when
        DiaryResponse res = diaryService.create(1L, req);

        // then — 반환 DTO 필드 검증
        assertThat(res.getContent()).isEqualTo("좋은 하루");
        assertThat(res.getEmotionId()).isEqualTo(5);

        // then — Repository 에 '무엇이' 저장되려 했는지 ArgumentCaptor 로 들여다보기
        ArgumentCaptor<Diary> captor = ArgumentCaptor.forClass(Diary.class);
        verify(diaryRepository).save(captor.capture());
        Diary saved = captor.getValue();

        assertThat(saved.getContent()).isEqualTo("좋은 하루");
        assertThat(saved.getEmotionId()).isEqualTo(5);
        assertThat(saved.getDate()).isEqualTo(1_700_000_000_000L);
        assertThat(saved.getUser()).isSameAs(user);
    }

    @Test
    @DisplayName("User 가 없으면 IllegalStateException 을 던지고 Diary 는 저장되지 않는다")
    void create_userNotFound_throws() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> diaryService.create(99L, newRequest(1L, "x", 1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("User not found");

        verify(diaryRepository, org.mockito.Mockito.never()).save(any());
    }

    // ---- 테스트 픽스처 헬퍼 ----
    private DiaryRequest newRequest(long date, String content, int emotionId) throws RuntimeException {
        try {
            DiaryRequest r = new DiaryRequest();
            setField(r, "date", date);
            setField(r, "content", content);
            setField(r, "emotionId", emotionId);
            return r;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private User mockUser(long id) throws Exception {
        User u = org.mockito.Mockito.mock(User.class);
        return u;
    }

    // DTO에는 setter가 없어서 리플렉션으로 처리
    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    @Test
    @DisplayName("DB 저장 실패 시 예외가 그대로 전파된다")
    void create_repositoryFails_propagates() {
        User user = org.mockito.Mockito.mock(User.class);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(diaryRepository.save(any(Diary.class)))
                .thenThrow(new DataIntegrityViolationException("UK"));

        assertThatThrownBy(() -> diaryService.create(1L, newRequest(1L, "x", 1)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("BDD 패턴으로 변경")
    void create_success2() throws Exception {
        // given
        User user = mockUser(1L);
        DiaryRequest req = newRequest(1_700_000_000_000L, "좋은 하루", 5);
        // BDD 스타일
        // when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        // save() 에 들어온 엔티티를 그대로 돌려주도록 stubbing
        when(diaryRepository.save(any(Diary.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // when
        DiaryResponse res = diaryService.create(1L, req);

        // then — 반환 DTO 필드 검증
        assertThat(res.getContent()).isEqualTo("좋은 하루");
        assertThat(res.getEmotionId()).isEqualTo(5);

        // then — Repository 에 '무엇이' 저장되려 했는지 ArgumentCaptor 로 들여다보기
        ArgumentCaptor<Diary> captor = ArgumentCaptor.forClass(Diary.class);
        // verify(diaryRepository).save(captor.capture());
        then(diaryRepository).should().save(captor.capture());
        Diary saved = captor.getValue();

        assertThat(saved.getContent()).isEqualTo("좋은 하루");
        assertThat(saved.getEmotionId()).isEqualTo(5);
        assertThat(saved.getDate()).isEqualTo(1_700_000_000_000L);
        assertThat(saved.getUser()).isSameAs(user);
    }
}