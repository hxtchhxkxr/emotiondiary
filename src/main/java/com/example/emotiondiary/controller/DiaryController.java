package com.example.emotiondiary.controller;

import com.example.emotiondiary.dto.DiaryListResponse;
import com.example.emotiondiary.dto.DiaryRequest;
import com.example.emotiondiary.dto.DiaryResponse;
import com.example.emotiondiary.security.dto.CustomUserDetails;
import com.example.emotiondiary.service.DiaryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/diaries")
@RequiredArgsConstructor
public class DiaryController {

    private final DiaryService diaryService;

    @GetMapping
    public ResponseEntity<DiaryListResponse> list(
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestParam Long from,
            @RequestParam Long to,
            @RequestParam(defaultValue = "latest") String sort) {
        return ResponseEntity.ok(diaryService.list(principal.getId(), from, to, sort));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DiaryResponse> getById(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable String id) {
        return ResponseEntity.ok(diaryService.getById(principal.getId(), id));
    }

    @PostMapping
    public ResponseEntity<DiaryResponse> create(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Valid @RequestBody DiaryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(diaryService.create(principal.getId(), request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DiaryResponse> update(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable String id,
            @Valid @RequestBody DiaryRequest request) {
        return ResponseEntity.ok(diaryService.update(principal.getId(), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable String id) {
        diaryService.delete(principal.getId(), id);
        return ResponseEntity.noContent().build();
    }
}