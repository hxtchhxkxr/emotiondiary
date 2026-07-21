package com.example.emotiondiary.repository;

import com.example.emotiondiary.entity.Diary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DiaryRepository extends JpaRepository<Diary, String> {

    List<Diary> findByDateBetweenOrderByDateDesc(Long from, Long to);

    List<Diary> findByDateBetweenOrderByDateAsc(Long from, Long to);
}