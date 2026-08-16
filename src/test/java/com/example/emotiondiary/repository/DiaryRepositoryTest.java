package com.example.emotiondiary.repository;

import com.example.emotiondiary.entity.Diary;
import com.example.emotiondiary.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import jakarta.persistence.EntityManager;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DiaryRepository @DataJpaTest")
@DataJpaTest // JPA 관련 빈(EntityManager, Repository, DataSource 등)만 로드, 각 메서드 자동 롤백
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE) // @DataJpaTest 는 기본적으로 운영 DataSource 를 내장 DB 로 "교체"하는데, 이설정 해제(application-test 사용)
class DiaryRepositoryTest {

    @Autowired DiaryRepository diaryRepository;
    @Autowired EntityManager em;   // 영속성 컨텍스트 직접 조작용 (persist/flush/clear)

    /**
     * 날짜 범위 + 최신순 정렬 쿼리 검증
     */
    @Test
    @DisplayName("findByUser_IdAndDateBetweenOrderByDateDesc — 기간 내 일기를 최신순으로 돌려준다")
    void findByDateBetweenDesc() {
        // given — Diary 가 User 를 FK 로 참조하므로 User 부터 저장해야 한다
        User user = User.create("a@b.com", "enc-pw", "홍길동");
        em.persist(user);

        // 범위(100~300) 안 3건 + 범위 밖(400) 1건 — 총 4건 저장
        em.persist(Diary.builder().user(user).date(100L).content("첫째").emotionId(1).build());
        em.persist(Diary.builder().user(user).date(200L).content("둘째").emotionId(2).build());
        em.persist(Diary.builder().user(user).date(300L).content("셋째").emotionId(3).build());
        em.persist(Diary.builder().user(user).date(400L).content("범위밖").emotionId(4).build());

        // flush: 쌓아둔 INSERT 를 DB 로 즉시 반영
        // clear: 1차 캐시를 비워서, 이후 조회가 실제 SELECT 쿼리를 타도록 강제
        //        (캐시에서 꺼내면 쿼리 자체가 검증되지 않는다)
        em.flush();
        em.clear();

        // when — date 가 100 이상 300 이하인 것만, 최신순으로 조회
        List<Diary> list = diaryRepository
                .findByUser_IdAndDateBetweenOrderByDateDesc(user.getId(), 100L, 300L);

        // then — 범위 안 3건만 조회되고, 정렬은 date 내림차순(셋째 → 둘째 → 첫째)
        assertThat(list).hasSize(3)
                .extracting(Diary::getContent)
                .containsExactly("셋째", "둘째", "첫째");
    }

    /**
     * 소유권 검증 쿼리 테스트
     */
    @Test
    @DisplayName("findByIdAndUser_Id — 본인 소유 일기만 조회된다")
    void findByIdAndUser_returnsOnlyOwner() {
        // given — 두 명의 사용자와, 내 것인 일기 1건
        //         em.merge() 는 detached 엔티티를 영속 상태로 복사·반환
        User me  = em.merge(User.create("me@b.com",  "p", "나"));
        User you = em.merge(User.create("you@b.com", "p", "너"));
        Diary myDiary = Diary.builder().user(me).date(1L).content("내 일기").emotionId(1).build();
        em.persist(myDiary);
        em.flush();

        // then — 본인(me) 으로 조회하면 조회됨, 타인(you) 으로 조회하면 비어 있음
        assertThat(diaryRepository.findByIdAndUser_Id(myDiary.getId(), me.getId())).isPresent();
        assertThat(diaryRepository.findByIdAndUser_Id(myDiary.getId(), you.getId())).isEmpty();
    }
}
