package com.example.emotiondiary.service;

import com.example.emotiondiary.dto.UserSummary;
import com.example.emotiondiary.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    public List<UserSummary> listAll() {
        return userRepository.findAll().stream()
                .map(UserSummary::from)
                .toList();
    }
}