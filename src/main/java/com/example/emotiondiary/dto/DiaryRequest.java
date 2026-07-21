package com.example.emotiondiary.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class DiaryRequest {

    @NotNull(message = "date is required")
    private Long date;

    @NotNull(message = "content is required")
    @Size(min = 1, max = 2000, message = "content must be between 1 and 2000 characters")
    private String content;

    @NotNull(message = "emotionId is required")
    @Min(value = 1, message = "emotionId must be between 1 and 5")
    @Max(value = 5, message = "emotionId must be between 1 and 5")
    private Integer emotionId;
}