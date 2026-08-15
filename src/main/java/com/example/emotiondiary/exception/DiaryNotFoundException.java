package com.example.emotiondiary.exception;

public class DiaryNotFoundException extends BusinessException {

    public DiaryNotFoundException(String id) {
        super(ErrorCode.DIARY_NOT_FOUND, "Diary not found with id: " + id);
    }
}