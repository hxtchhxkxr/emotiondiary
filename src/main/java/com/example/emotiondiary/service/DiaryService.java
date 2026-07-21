package com.example.emotiondiary.service;

import com.example.emotiondiary.dto.DiaryListResponse;
import com.example.emotiondiary.dto.DiaryRequest;
import com.example.emotiondiary.dto.DiaryResponse;
import com.example.emotiondiary.entity.Diary;
import com.example.emotiondiary.exception.DiaryNotFoundException;
import com.example.emotiondiary.repository.DiaryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 일기(Diary) 관련 비즈니스 로직을 담당하는 서비스 클래스
 *
 * - @Service: 이 클래스가 Spring의 서비스 계층 빈(Bean)임을 나타냄
 * - @RequiredArgsConstructor: final 필드에 대한 생성자를 자동 생성 (생성자 주입)
 * - @Transactional(readOnly = true): 클래스 레벨 기본 트랜잭션을 "읽기 전용"으로 설정
 *   → 읽기 전용 트랜잭션은 JPA 변경 감지(dirty checking)를 건너뛰어 성능이 향상됨
 *   → 쓰기가 필요한 메서드에만 개별적으로 @Transactional을 붙여 읽기 전용을 해제함
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DiaryService {

    private final DiaryRepository diaryRepository;

    /**
     * 일기 목록 조회
     *
     * @param from 조회 시작 날짜 (epoch 등 숫자 형태)
     * @param to   조회 종료 날짜
     * @param sort 정렬 방식 ("oldest" → 오래된 순, 그 외 → 최신 순)
     * @return 일기 목록 응답 DTO
     */
    public DiaryListResponse list(Long from, Long to, String sort) {
        // 정렬 파라미터에 따라 다른 Repository 메서드를 호출
        // Spring Data JPA의 쿼리 메서드 네이밍 규칙으로 SQL 없이 자동 구현됨
        List<Diary> diaries = "oldest".equals(sort)
                ? diaryRepository.findByDateBetweenOrderByDateAsc(from, to)
                : diaryRepository.findByDateBetweenOrderByDateDesc(from, to);

        // Entity → Response DTO 변환 (Stream API 사용)
        // 엔티티를 직접 반환하지 않고 DTO로 변환하여 계층 간 의존성을 분리
        List<DiaryResponse> items = diaries.stream()
                .map(DiaryResponse::from)  // 메서드 참조: DiaryResponse.from(diary)와 동일
                .toList();

        // Builder 패턴으로 응답 객체 생성
        return DiaryListResponse.builder()
                .items(items)
                .total(items.size())
                .build();
    }

    /**
     * 일기 단건 조회
     *
     * @param id 조회할 일기 ID
     * @return 일기 응답 DTO
     * @throws IllegalArgumentException 해당 ID의 일기가 없을 경우
     */
    public DiaryResponse getById(String id) {
        // orElseThrow: Optional이 비어있으면 예외를 던짐
        // → 존재하지 않는 일기를 조회하려 하면 에러 발생
        Diary diary = diaryRepository.findById(id)
                .orElseThrow(() -> new DiaryNotFoundException(id));
        return DiaryResponse.from(diary);
    }

    /**
     * 일기 생성
     *
     * @Transactional: 이 메서드는 데이터를 변경(INSERT)하므로 쓰기 가능한 트랜잭션 필요
     * → 클래스 레벨의 readOnly = true를 이 메서드에서만 덮어씀(override) readOnly = false로 됨
     */
    @Transactional
    public DiaryResponse create(DiaryRequest request) {
        // Builder 패턴으로 Diary 엔티티 생성
        Diary diary = Diary.builder()
                .date(request.getDate())
                .content(request.getContent())
                .emotionId(request.getEmotionId())
                .build();
        // save() 호출 → JPA가 INSERT 쿼리를 실행하고, 저장된 엔티티를 반환
        return DiaryResponse.from(diaryRepository.save(diary));
    }

    /**
     * 일기 수정
     *
     * @Transactional: 쓰기 가능한 트랜잭션 내에서 실행
     * → 트랜잭션 안에서 엔티티를 수정하면 JPA 변경 감지(dirty checking)에 의해
     *   트랜잭션 커밋 시점에 자동으로 UPDATE 쿼리가 실행됨 (별도 save() 호출 불필요)
     */
    @Transactional
    public DiaryResponse update(String id, DiaryRequest request) {
        Diary diary = diaryRepository.findById(id)
                .orElseThrow(() -> new DiaryNotFoundException(id));
        // 엔티티의 update 메서드를 호출하여 필드 값 변경
        // → 트랜잭션 종료 시 JPA가 변경을 감지하고 자동으로 UPDATE SQL 실행
        diary.update(request.getDate(), request.getContent(), request.getEmotionId());
        return DiaryResponse.from(diary);
    }

    /**
     * 일기 삭제
     *
     * @Transactional: 쓰기 가능한 트랜잭션 (DELETE 쿼리 실행)
     */
    @Transactional
    public void delete(String id) {
        // 삭제 전에 먼저 존재 여부를 확인 → 없으면 예외 발생
        Diary diary = diaryRepository.findById(id)
                .orElseThrow(() -> new DiaryNotFoundException(id));
        diaryRepository.delete(diary);
    }
}