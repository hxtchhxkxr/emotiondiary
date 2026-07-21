package com.example.emotiondiary.exception;

public class DiaryNotFoundException extends RuntimeException {

    public DiaryNotFoundException(String id) {
        super("Diary not found with id: " + id);
    }
}
