package com.example.emotiondiary.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "diary")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Diary {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false)
    private Long date;            // epoch millis (yyyyMMdd 같은 형태도 가능)

    @Column(nullable = false, length = 2000)
    private String content;

    @Column(name = "emotion_id", nullable = false)
    private Integer emotionId;    // 1~5

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Builder
    public Diary(Long date, String content, Integer emotionId) {
        this.date = date;
        this.content = content;
        this.emotionId = emotionId;
    }

    @PrePersist // INSERT 전
    protected void onCreate() {
        this.id = UUID.randomUUID().toString();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate // UPDATE 전
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void update(Long date, String content, Integer emotionId) {
        this.date = date;
        this.content = content;
        this.emotionId = emotionId;
    }
}