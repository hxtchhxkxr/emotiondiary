package com.example.emotiondiary.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ ElementType.FIELD }) // 필드에 붙이는 어노테이션
@Retention(RetentionPolicy.RUNTIME) // 실행중 유지
@Constraint(validatedBy = UniqueEmailValidator.class) // 검증 클래스
public @interface UniqueEmail {
    String message() default "이미 가입된 이메일입니다"; // 기본 메시지
    Class<?>[] groups() default {}; // 그룹 지정
    Class<? extends Payload>[] payload() default {}; // 메타데이터
}