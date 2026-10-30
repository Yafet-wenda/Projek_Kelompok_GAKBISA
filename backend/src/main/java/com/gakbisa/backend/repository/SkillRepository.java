package com.gakbisa.backend.repository;

import com.gakbisa.backend.domain.Skill;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SkillRepository extends JpaRepository<Skill, UUID> {

    Optional<Skill> findByNameIgnoreCase(String name);

    // open-in-view dimatikan, jadi kategori harus ikut diambil atau proxy-nya
    // meledak saat controller mengubah entity menjadi JSON.
    @EntityGraph(attributePaths = "category")
    Page<Skill> findAllBy(Pageable pageable);

    @EntityGraph(attributePaths = "category")
    Page<Skill> findByCategory_Id(UUID categoryId, Pageable pageable);

    @EntityGraph(attributePaths = "category")
    @Query("""
        select s from Skill s
        where lower(s.name) like lower(concat('%', :keyword, '%'))
           or lower(s.category.name) like lower(concat('%', :keyword, '%'))
        """)
    Page<Skill> search(@Param("keyword") String keyword, Pageable pageable);

    List<Skill> findByCategory_Id(UUID categoryId);
}
