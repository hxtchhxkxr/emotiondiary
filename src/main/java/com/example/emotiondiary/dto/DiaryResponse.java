package com.example.emotiondiary.dto;

import com.example.emotiondiary.entity.Diary;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DiaryResponse {

    private String id;
    private Long date;
    private String content;
    private Integer emotionId;

    public static DiaryResponse from(Diary diary) {
        return DiaryResponse.builder()
                .id(diary.getId())
                .date(diary.getDate())
                .content(diary.getContent())
                .emotionId(diary.getEmotionId())
                .build();
    }
}