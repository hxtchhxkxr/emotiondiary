package com.example.emotiondiary.controller;

import com.example.emotiondiary.dto.DiaryListResponse;
import com.example.emotiondiary.dto.DiaryRequest;
import com.example.emotiondiary.dto.DiaryResponse;
import com.example.emotiondiary.service.DiaryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/diaries")
@RequiredArgsConstructor
public class DiaryController {

    private final DiaryService diaryService;

    // 시큐리티 에서 제거예정(AuthenticationPrincipal)
    private static final Long TEMP_USER_ID = 1L;

    @GetMapping
    public ResponseEntity<DiaryListResponse> list(
            @RequestParam Long from,
            @RequestParam Long to,
            @RequestParam(defaultValue = "latest") String sort) {
        return ResponseEntity.ok(diaryService.list(TEMP_USER_ID, from, to, sort));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DiaryResponse> getById(@PathVariable String id) {
        return ResponseEntity.ok(diaryService.getById(TEMP_USER_ID, id));
    }

    @PostMapping
    public ResponseEntity<DiaryResponse> create(@Valid @RequestBody DiaryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(diaryService.create(TEMP_USER_ID, request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DiaryResponse> update(
            @PathVariable String id,
            @Valid @RequestBody DiaryRequest request) {
        return ResponseEntity.ok(diaryService.update(TEMP_USER_ID, id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        diaryService.delete(TEMP_USER_ID, id);
        return ResponseEntity.noContent().build();
    }
}