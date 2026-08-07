package com.example.emotiondiary.repository;

import com.example.emotiondiary.entity.Diary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DiaryRepository extends JpaRepository<Diary, String> {

    List<Diary> findByUser_IdAndDateBetweenOrderByDateDesc(Long userId, Long from, Long to);

    List<Diary> findByUser_IdAndDateBetweenOrderByDateAsc(Long userId, Long from, Long to);

    Optional<Diary> findByIdAndUser_Id(String id, Long userId);
}