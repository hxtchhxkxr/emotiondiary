package com.example.emotiondiary.service;

import com.example.emotiondiary.entity.Diary;
import com.example.emotiondiary.exception.DiaryNotFoundException;
import com.example.emotiondiary.repository.DiaryRepository;
import com.example.emotiondiary.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("DiaryService.delete() 행위 검증")
@ExtendWith(MockitoExtension.class)
class DiaryServiceDeleteTest {

    @Mock DiaryRepository diaryRepository;
    @Mock UserRepository userRepository;

    @InjectMocks DiaryService diaryService;

    @Nested
    @DisplayName("일기가 존재할 때")
    class WhenDiaryExists {

        @Test
        @DisplayName("Repository.delete() 가 정확히 1회 호출된다")
        void delete_callsRepositoryOnce() {
            Diary diary = Diary.builder().date(100L).content("x").emotionId(1).build();
            when(diaryRepository.findByIdAndUser_Id(eq("id-1"), anyLong()))
                    .thenReturn(Optional.of(diary));

            diaryService.delete(1L, "id-1");

            verify(diaryRepository, times(1)).delete(diary);
        }
    }

    @Nested
    @DisplayName("일기가 없을 때")
    class WhenDiaryMissing {

        @Test
        @DisplayName("DiaryNotFoundException 을 던지고 delete() 는 호출되지 않는다")
        void delete_missing_throwsAndSkipsDelete() {
            when(diaryRepository.findByIdAndUser_Id(eq("nope"), anyLong()))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> diaryService.delete(1L, "nope"))
                    .isInstanceOf(DiaryNotFoundException.class);

            verify(diaryRepository, never()).delete(any());
        }
    }
}