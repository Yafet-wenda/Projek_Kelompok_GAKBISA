package com.gakbisa.backend.controller;

import com.gakbisa.backend.repository.SkillCategoryRepository;
import com.gakbisa.backend.repository.SkillRepository;
import com.gakbisa.backend.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class SkillsControllerTest {

    private SkillRepository skills;
    private SkillsController controller;

    @BeforeEach
    void setup() {
        skills = mock(SkillRepository.class);
        controller = new SkillsController(mock(UserService.class), skills, mock(SkillCategoryRepository.class));

        when(skills.findAllBy(any(PageRequest.class))).thenReturn(Page.empty());
        when(skills.search(anyString(), any(PageRequest.class))).thenReturn(Page.empty());
        when(skills.findByCategory_Id(any(UUID.class), any(PageRequest.class))).thenReturn(Page.empty());
        when(skills.searchByCategory(any(UUID.class), anyString(), any(PageRequest.class))).thenReturn(Page.empty());
    }

    @Test
    void katalogTanpaFilterMemakaiFindAll() {
        controller.catalog(null, null, 0, 20);

        verify(skills).findAllBy(PageRequest.of(0, 20));
        verifyNoMoreInteractions(skills);
    }

    @Test
    void katalogDenganKeywordMemakaiSearchNonNull() {
        controller.catalog("  Java  ", null, 0, 20);

        verify(skills).search("Java", PageRequest.of(0, 20));
        verifyNoMoreInteractions(skills);
    }

    @Test
    void katalogDenganKategoriMemakaiFindByCategory() {
        UUID categoryId = UUID.randomUUID();
        controller.catalog(null, categoryId, 0, 20);

        verify(skills).findByCategory_Id(categoryId, PageRequest.of(0, 20));
        verifyNoMoreInteractions(skills);
    }

    @Test
    void katalogDenganKategoriDanKeywordMemakaiSearchByCategory() {
        UUID categoryId = UUID.randomUUID();
        controller.catalog("Java", categoryId, 0, 20);

        verify(skills).searchByCategory(categoryId, "Java", PageRequest.of(0, 20));
        verifyNoMoreInteractions(skills);
    }

    @Test
    void keywordKosongSetelahTrimMemakaiFindAll() {
        controller.catalog("   ", null, 0, 20);

        verify(skills).findAllBy(PageRequest.of(0, 20));
        verifyNoMoreInteractions(skills);
    }
}
