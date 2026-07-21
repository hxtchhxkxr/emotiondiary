package com.example.emotiondiary.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class DiaryRequest {

    private Long date;
    private String content;
    private Integer emotionId;
}