package com.gakbisa.service;

import com.gakbisa.dto.Forms.PortfolioForm;
import com.gakbisa.dto.Views.FileContent;
import com.gakbisa.dto.Views.PortfolioView;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

public interface PortfolioService {
    /** Validasi ekstensi/MIME/ukuran -> upload ke Supabase Storage -> simpan metadata ke Supabase PostgreSQL. */
    void upload(String email, PortfolioForm form, MultipartFile file);
    List<PortfolioView> listMine(String email);
    Page<PortfolioView> publicCatalog(String keyword, UUID categoryId, Pageable pageable);
    /** Hapus objek di Storage + metadata. Hanya pemilik. */
    void delete(String email, UUID portfolioId);
    /** Cek hak akses (PUBLIC / pemilik / admin; hidden hanya admin & pemilik). email boleh null untuk tamu. */
    FileContent loadFile(String emailOrNull, UUID portfolioId);
}
