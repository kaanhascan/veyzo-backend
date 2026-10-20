package com.veyzo.media_editing_system.util; // Kendi paket adına göre düzenle

import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

public class FileValidator {


    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList("mp4", "mov");

    public static void validateVideoFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Dosya boş olamaz.");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.contains(".")) {
            throw new IllegalArgumentException("Geçersiz dosya formatı.");
        }

        String extension = filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Sadece MP4 ve MOV formatları desteklenmektedir.");
        }

        try (InputStream is = file.getInputStream()) {
            byte[] header = new byte[8];

            if (is.read(header) < 8) {
                throw new IllegalArgumentException("Dosya okunamadı veya çok küçük.");
            }

            boolean isFtyp = header[4] == 0x66 && header[5] == 0x74 &&
                    header[6] == 0x79 && header[7] == 0x70;

            if (!isFtyp) {
                throw new IllegalArgumentException("Dosya içeriği geçersiz! Uzantısı değiştirilmiş sahte dosya tespit edildi.");
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (IOException e) {
            throw new IllegalArgumentException("Dosya doğrulanırken bir hata oluştu: " + e.getMessage());
        }
    }
}
