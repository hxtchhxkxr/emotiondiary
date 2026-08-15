package com.example.emotiondiary.controller;

import com.example.emotiondiary.dto.UserSummary;
import com.example.emotiondiary.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')") // ROLE_ADMIN만 접속 가능
public class AdminUserController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<List<UserSummary>> list() {
        return ResponseEntity.ok(userService.listAll());
    }
}