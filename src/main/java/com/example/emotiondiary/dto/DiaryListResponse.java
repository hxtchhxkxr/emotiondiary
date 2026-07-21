package com.example.emotiondiary.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class DiaryListResponse {

    private List<DiaryResponse> items;
    private int total;
}