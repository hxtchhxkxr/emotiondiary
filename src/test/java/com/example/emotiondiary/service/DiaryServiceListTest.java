package com.example.emotiondiary.service;

import com.example.emotiondiary.dto.DiaryListResponse;
import com.example.emotiondiary.dto.DiaryResponse;
import com.example.emotiondiary.entity.Diary;
import com.example.emotiondiary.entity.User;
import com.example.emotiondiary.repository.DiaryRepository;
import com.example.emotiondiary.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@DisplayName("DiaryService.list() 단위 테스트")
@ExtendWith(MockitoExtension.class)
class DiaryServiceListTest {

    // 가짜 객체
    @Mock DiaryRepository diaryRepository;
    @Mock UserRepository userRepository;

    // 생성자 주입
    @InjectMocks DiaryService diaryService;

    @Test
    @DisplayName("sort=desc 일 때 최신순 Repository 메서드를 호출해 결과를 돌려준다")
    void list_desc_returnsMappedItems() {
        // given — Repository 의 반환값을 준비
        List<Diary> fake = List.of(
                Diary.builder().date(300L).content("셋째").emotionId(3).build(),
                Diary.builder().date(200L).content("둘째").emotionId(2).build(),
                Diary.builder().date(100L).content("첫째").emotionId(1).build()
        );
        // 리턴값 미리 세팅(stubbing)
        when(diaryRepository.findByUser_IdAndDateBetweenOrderByDateDesc(
                eq(1L), anyLong(), anyLong()))
                .thenReturn(fake);

        // when (list메서드는 진짜, findByUser_IdAndDateBetweenOrderByDateDesc는 가짜)
        DiaryListResponse res = diaryService.list(1L, 0L, 999L, "desc");

        // then — Service 가 DTO 로 매핑했는지만 검증 (DB 는 아예 뜨지 않았음)
        assertThat(res.getTotal()).isEqualTo(3);
        assertThat(res.getItems())
                .extracting("content")
                .containsExactly("셋째", "둘째", "첫째");
    }

    @Test
    void anyMatchers() {
        User user = User.create("홍길동", "1111", "홍");
        Diary diary = Diary.builder().date(100L).content("첫째").emotionId(1).build();
        when(diaryRepository.findByIdAndUser_Id(any(String.class), anyLong())).thenReturn(Optional.of(diary));
        DiaryResponse res = diaryService.getById(1L,"");
        System.out.println(res);
    }
}
