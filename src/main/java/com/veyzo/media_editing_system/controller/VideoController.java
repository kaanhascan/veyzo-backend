package com.veyzo.media_editing_system.controller;

import com.veyzo.media_editing_system.model.OperationType;
import com.veyzo.media_editing_system.model.Video;
import com.veyzo.media_editing_system.model.VideoStatus;

import com.veyzo.media_editing_system.repository.VideoRepository;
import com.veyzo.media_editing_system.service.VideoService;
import com.veyzo.media_editing_system.util.FileValidator;
import com.veyzo.media_editing_system.dto.response.VideoListResponse;
import com.veyzo.media_editing_system.dto.response.VideoStatusResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;



@RestController
@RequestMapping("/api/videos")
@RequiredArgsConstructor
public class VideoController {

    private final VideoService videoService;
    private final VideoRepository videoRepository;

    @PostMapping(value = "/upload")
    public ResponseEntity<?> uploadVideo(
            @RequestParam("file") MultipartFile file,
            @RequestParam("title") String title,
            @RequestParam("startTime") String startTime,
            @RequestParam("duration") String duration,
            @RequestParam(value = "batchId", required = false) String batchId
    ) {
        try {
            FileValidator.validateVideoFile(file);
            videoService.uploadVideo(file, title, startTime, duration, batchId, OperationType.TRIM);
            return ResponseEntity.ok("Video başarıyla yüklendi");

        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Dosya yüklenirken bir hata oluştu: " + e.getMessage());

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Beklenmeyen bir hata oluştu: " + e.getMessage());
        }
    }

    @PostMapping(value = "/extract-audio")
    public ResponseEntity<?> extractAudio(
            @RequestParam("file") MultipartFile file,
            @RequestParam("title") String title,
            @RequestParam(value = "batchId", required = false) String batchId
    ) {
        try {
            FileValidator.validateVideoFile(file);
            videoService.uploadForAudio(file, title, batchId,OperationType.AUDIO);
            return ResponseEntity.ok("Video başarıyla yüklendi, ses ayırma işlemi başladı");

        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Dosya yüklenirken bir hata oluştu: " + e.getMessage());

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Beklenmeyen bir hata oluştu: " + e.getMessage());
        }
    }

    @GetMapping("/{videoId}/status")
    public ResponseEntity<VideoStatusResponse> getVideoStatus(@PathVariable UUID videoId) {
        return ResponseEntity.ok(videoService.getVideoStatus(videoId));
    }

    @GetMapping(value = "/download/batch/{batchId}", produces = "application/zip")
    public ResponseEntity<StreamingResponseBody> downloadBatchAsZip(@PathVariable String batchId) {

        if (batchId == null || !batchId.matches("^[a-zA-Z0-9-]+$")) {
            throw new IllegalArgumentException("Geçersiz klasör ID formatı.");
        }

        String userEmail = SecurityContextHolder.getContext().getAuthentication().getName();

        List<Video> videos = videoService.getVideosForBatch(batchId, userEmail);

        StreamingResponseBody stream = outputStream -> {
            videoService.createZipForVideos(videos, outputStream);
        };

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"veyzo_klasor_" + batchId + ".zip\"")
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(stream);
    }

    @GetMapping("/download/{videoId}")
    public ResponseEntity<Resource> downloadVideo(@PathVariable UUID videoId) {
        Video video = videoService.getOwnedVideo(videoId);

        if (video.getStatus() != VideoStatus.COMPLETED) {
            throw new RuntimeException("Video henüz hazır değil.");
        }

        File file = new File(video.getProcessedFilePath());
        Resource resource = new FileSystemResource(file);

        String processedPath = video.getProcessedFilePath();
        String extension = processedPath != null && processedPath.contains(".")
                ? processedPath.substring(processedPath.lastIndexOf("."))
                : ".mp4"; // Fallback

        String safeTitle = video.getTitle().replaceAll("[^a-zA-Z0-9.-]", "_");
        String downloadName = safeTitle + extension;

        MediaType mediaType;
        if (extension.equalsIgnoreCase(".mp3")) {
            mediaType = MediaType.parseMediaType("audio/mpeg");
        } else if (extension.equalsIgnoreCase(".gif")) {
            mediaType = MediaType.parseMediaType("image/gif");
        } else if (extension.equalsIgnoreCase(".zip")) {
            mediaType = MediaType.parseMediaType("application/zip");
        } else {
            mediaType = MediaType.parseMediaType("video/mp4");
        }

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + downloadName + "\"")
                .header("Access-Control-Expose-Headers", "Content-Disposition")
                .body(resource);
    }

    @GetMapping("/my-videos")
    public ResponseEntity<List<VideoListResponse>> getMyVideos() {
        return ResponseEntity.ok(videoService.getUserVideos());
    }

    @DeleteMapping("/{videoId}")
    public ResponseEntity<String> deletevideo(@PathVariable UUID videoId) {
        try{
            videoService.deleteVideo(videoId);
            return ResponseEntity.ok("Video başarıyla silindi");
        }
        catch (Exception e){
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping(value = "/merge", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> mergeVideos(
            @RequestParam(value = "files", required = false) MultipartFile[] files,
            @RequestParam(value = "existingVideoIds", required = false) List<String> existingVideoIds,
            @RequestParam("title") String title,
            @RequestParam(value = "batchId", required = false) String batchId
    ) {
        try {
            List<UUID> uuidList = null;
            if (existingVideoIds != null && !existingVideoIds.isEmpty()) {
                uuidList = existingVideoIds.stream()
                        .map(UUID::fromString)
                        .toList();
            }
            if(files != null){
                for(MultipartFile file : files){
                    FileValidator.validateVideoFile(file);
                }
            }

            videoService.mergeVideos(files, uuidList, title, batchId,OperationType.MERGE);
            return ResponseEntity.ok("Video birleştirme işlemi başarıyla kuyruğa alındı.");

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Birleştirme hatası: " + e.getMessage());
        }
    }

    @PostMapping(value = "/extract-gif", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> extractGif(
            @RequestParam("file") MultipartFile file,
            @RequestParam("title") String title,
            @RequestParam(value = "batchId", required = false) String batchId
    ) {
        try {
            FileValidator.validateVideoFile(file);
            videoService.uploadForGif(file, title, null,OperationType.GIF);
            return ResponseEntity.ok("GIF dönüştürme işlemi başarıyla kuyruğa alındı.");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("GIF yükleme hatası: " + e.getMessage());
        }
    }

    @PostMapping(value = "/compress", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> compressVideo(
            @RequestParam("file") MultipartFile file,
            @RequestParam("title") String title
    ) {
        try {
            FileValidator.validateVideoFile(file);
            videoService.uploadForCompression(file, title,OperationType.COMPRESS);
            return ResponseEntity.ok("Sıkıştırma işlemi başarıyla kuyruğa alındı.");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Video yükleme hatası: " + e.getMessage());
        }
    }
}