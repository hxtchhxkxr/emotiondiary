package com.example.emotiondiary.controller;

import com.example.emotiondiary.dto.DiaryListResponse;
import com.example.emotiondiary.dto.DiaryRequest;
import com.example.emotiondiary.dto.DiaryResponse;
import com.example.emotiondiary.service.DiaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/diaries")
@RequiredArgsConstructor
public class DiaryController {

    private final DiaryService diaryService;

    @GetMapping
    public ResponseEntity<DiaryListResponse> list(
            @RequestParam Long from,
            @RequestParam Long to,
            @RequestParam(defaultValue = "latest") String sort) {
        return ResponseEntity.ok(diaryService.list(from, to, sort));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DiaryResponse> getById(@PathVariable String id) {
        return ResponseEntity.ok(diaryService.getById(id));
    }

    @PostMapping
    public ResponseEntity<DiaryResponse> create(@RequestBody DiaryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(diaryService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DiaryResponse> update(
            @PathVariable String id,
            @RequestBody DiaryRequest request) {
        return ResponseEntity.ok(diaryService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        diaryService.delete(id);
        return ResponseEntity.noContent().build();
    }
}