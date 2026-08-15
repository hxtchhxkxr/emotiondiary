package com.example.emotiondiary.service;

import com.example.emotiondiary.dto.auth.SignUpRequest;
import com.example.emotiondiary.entity.User;
import com.example.emotiondiary.exception.BusinessException;
import com.example.emotiondiary.exception.ErrorCode;
import com.example.emotiondiary.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;

    @Transactional
    public Long signup(SignUpRequest request) {
        // @UniqueEmail 가 1차 방어. 동시 요청 대비 한 번 더 확인.
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
        User user = User.create(
                request.getEmail(),
                request.getPassword(),     // 뒤에서 BCrypt 로 인코딩
                request.getNickname());
        return userRepository.save(user).getId();
    }
}
