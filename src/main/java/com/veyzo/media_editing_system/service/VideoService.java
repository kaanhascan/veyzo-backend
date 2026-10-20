package com.veyzo.media_editing_system.service;

import com.veyzo.media_editing_system.exception.BusinessException;
import com.veyzo.media_editing_system.model.OperationType;
import com.veyzo.media_editing_system.model.User;
import com.veyzo.media_editing_system.model.Video;
import com.veyzo.media_editing_system.model.VideoStatus;
import com.veyzo.media_editing_system.repository.UserRepository;
import com.veyzo.media_editing_system.repository.VideoRepository;
import com.veyzo.media_editing_system.dto.response.UploadResponse;
import com.veyzo.media_editing_system.dto.response.VideoListResponse;
import com.veyzo.media_editing_system.dto.response.VideoStatusResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoService {

    private final VideoRepository videoRepository;
    private final UserRepository userRepository;
    private final FfmpegService ffmpegService;

    private final String UPLOAD_DIR = System.getProperty("user.dir") + "/uploads/raw/";

    public UploadResponse uploadVideo(MultipartFile file, String title, String startTime, String duration, String batchId, OperationType operationType) throws IOException {

        File directory = new File(UPLOAD_DIR);
        if (!directory.exists()) {
            directory.mkdirs();
        }

        String uniqueFileName = generateUniqueFileName(file.getOriginalFilename());
        Path filePath = Paths.get(UPLOAD_DIR, uniqueFileName);

        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        User currentUser = getCurrentUser();

        Video video = Video.builder()
                .title(title.trim())
                .originalFileName(uniqueFileName)
                .originalFilePath(filePath.toString())
                .status(VideoStatus.PENDING)
                .user(currentUser)
                .batchId(batchId)
                .operationType(operationType)
                .build();

        Video savedVideo = videoRepository.save(video);

        ffmpegService.processVideoTrimming(savedVideo.getId(), startTime.trim(), duration.trim());

        return new UploadResponse(
                savedVideo.getId(),
                savedVideo.getTitle(),
                savedVideo.getStatus(),
                "Video başarıyla yüklendi ve işleme sırasına alındı."
        );
    }

    public VideoStatusResponse getVideoStatus(UUID videoId) {
        Video video = getOwnedVideo(videoId);

        return new VideoStatusResponse(video.getStatus(), video.getProcessedFileName());
    }

    public List<VideoListResponse> getUserVideos() {
        User user = getCurrentUser();

        return videoRepository.findByUser_IdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(video -> new VideoListResponse(
                        video.getId(),
                        video.getTitle(),
                        video.getStatus(),
                        video.getCreatedAt(),
                        video.getBatchId(),
                        video.getOriginalFileName(),
                        video.getProcessedFileName(),
                        video.getOperationType()
                ))
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteVideo(UUID videoId) {
        Video video = getOwnedVideo(videoId);

        try {
            if (video.getOriginalFilePath() != null && !"virtual".equals(video.getOriginalFilePath())) {
                Files.deleteIfExists(Paths.get(video.getOriginalFilePath()));
            }
            if (video.getProcessedFilePath() != null) {
                Files.deleteIfExists(Paths.get(video.getProcessedFilePath()));
            }
        } catch (Exception e) {
            log.error("Dosya fiziksel olarak silinirken bir hata ile karşılaşıldı (videoId={}): {}", videoId, e.getMessage());
        }
        videoRepository.delete(video);
    }

    public List<Video> getVideosForBatch(String batchId, String userEmail) {
        User currentUser = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new BusinessException("Kullanıcı bulunamadı.", HttpStatus.NOT_FOUND));

        return videoRepository.findByBatchIdAndUser(batchId, currentUser);
    }

    public void createZipForVideos(List<Video> videos, OutputStream outputStream) {
        try (ZipOutputStream zos = new ZipOutputStream(outputStream)) {
            boolean hasFiles = false;

            for (Video video : videos) {
                if (video.getStatus() != VideoStatus.COMPLETED || video.getProcessedFilePath() == null) {
                    log.debug("ZIP için atlandı (statü uygun değil): {}", video.getTitle());
                    continue;
                }

                Path filePath = Paths.get(video.getProcessedFilePath());

                if (Files.exists(filePath)) {
                    String processedPath = video.getProcessedFilePath();
                    String extension = processedPath.contains(".")
                            ? processedPath.substring(processedPath.lastIndexOf("."))
                            : ".mp4";

                    String safeTitle = video.getTitle().replaceAll("[^a-zA-Z0-9_-]", "_");
                    String entryName = safeTitle + "_" + video.getId().toString().substring(0, 5) + extension;

                    ZipEntry zipEntry = new ZipEntry(entryName);
                    zos.putNextEntry(zipEntry);
                    Files.copy(filePath, zos);
                    zos.closeEntry();
                    hasFiles = true;
                } else {
                    log.warn("ZIP için dosya fiziksel olarak bulunamadı: {}", filePath.toAbsolutePath());
                }
            }

            if (!hasFiles) {
                ZipEntry errorEntry = new ZipEntry("bilgi.txt");
                zos.putNextEntry(errorEntry);
                zos.write("Bu klasördeki videolar henüz işlenmemiş, başarısız olmuş veya silinmiş olabilir.".getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }

        } catch (Exception e) {
            log.error("ZIP oluşturulurken hata: {}", e.getMessage(), e);
        }
    }

    public void uploadForAudio(MultipartFile file, String title, String batchId, OperationType operationType) throws IOException {
        File directory = new File(UPLOAD_DIR);
        if (!directory.exists()) {
            directory.mkdirs();
        }

        String uniqueFileName = generateUniqueFileName(file.getOriginalFilename());
        Path filePath = Paths.get(UPLOAD_DIR, uniqueFileName);

        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        User currentUser = getCurrentUser();

        Video video = Video.builder()
                .title(title.trim())
                .originalFileName(uniqueFileName)
                .originalFilePath(filePath.toString())
                .status(VideoStatus.PENDING)
                .user(currentUser)
                .batchId(batchId)
                .operationType(operationType)
                .build();

        Video savedVideo = videoRepository.save(video);

        ffmpegService.processAudioExtraction(savedVideo.getId());
    }

    public void mergeVideos(MultipartFile[] newFiles, List<UUID> existingVideoIds, String title, String batchId, OperationType operationType) throws IOException {

        int newFilesCount = 0;
        if (newFiles != null) {
            for (MultipartFile f : newFiles) {
                if (f != null && !f.isEmpty()) {
                    newFilesCount++;
                }
            }
        }
        int existingCount = (existingVideoIds != null) ? existingVideoIds.size() : 0;

        if (newFilesCount + existingCount < 2) {
            throw new IllegalArgumentException("Birleştirme işlemi için en az 2 video (yeni veya mevcut) seçmelisiniz.");
        }
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("Geçerli bir başlık girmelisiniz.");
        }

        User currentUser = getCurrentUser();
        List<UUID> allVideoIdsToMerge = new ArrayList<>();

        if (existingVideoIds != null && !existingVideoIds.isEmpty()) {
            Map<UUID, Video> videoMap = videoRepository.findAllById(existingVideoIds).stream()
                    .collect(Collectors.toMap(Video::getId, Function.identity()));

            for (UUID id : existingVideoIds) {
                Video v = videoMap.get(id);
                if (v == null) {
                    throw new BusinessException("Seçilen videolardan biri bulunamadı: " + id, HttpStatus.NOT_FOUND);
                }
                if (!v.getUser().getId().equals(currentUser.getId())) {
                    throw new BusinessException("Sadece kendi videolarınızı birleştirebilirsiniz!", HttpStatus.FORBIDDEN);
                }
                if (v.getStatus() != VideoStatus.COMPLETED) {
                    throw new BusinessException("Yalnızca işlemi tamamlanmış (COMPLETED) mevcut videolar birleştirilebilir.", HttpStatus.BAD_REQUEST);
                }
                allVideoIdsToMerge.add(v.getId());
            }
        }

        if (newFiles != null && newFiles.length > 0) {
            File directory = new File(UPLOAD_DIR);
            if (!directory.exists()) directory.mkdirs();

            for (MultipartFile file : newFiles) {
                if (file == null || file.isEmpty()) continue;

                String originalFilename = file.getOriginalFilename();
                String uniqueFileName = generateUniqueFileName(originalFilename);
                Path filePath = Paths.get(UPLOAD_DIR, uniqueFileName);

                Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

                Video newVideo = Video.builder()
                        .title(originalFilename)
                        .originalFileName(uniqueFileName)
                        .originalFilePath(filePath.toString())
                        .processedFileName(uniqueFileName)
                        .processedFilePath(filePath.toString())
                        .status(VideoStatus.COMPLETED)
                        .user(currentUser)
                        .batchId(batchId)
                        .operationType(operationType)
                        .build();

                Video savedVideo = videoRepository.save(newVideo);
                allVideoIdsToMerge.add(savedVideo.getId());
            }
        }

        Video mergedVideo = Video.builder()
                .title(title.trim())
                .originalFileName("merge_request_" + UUID.randomUUID())
                .originalFilePath("virtual")
                .status(VideoStatus.PENDING)
                .user(currentUser)
                .batchId(batchId)
                .operationType(operationType)
                .build();

        Video savedMergedVideo = videoRepository.save(mergedVideo);

        ffmpegService.processVideoMerging(savedMergedVideo.getId(), allVideoIdsToMerge);
    }

    public void uploadForGif(MultipartFile file, String title, String batchId, OperationType operationType) throws IOException {
        File directory = new File(UPLOAD_DIR);
        if (!directory.exists()) directory.mkdirs();

        String uniqueFileName = generateUniqueFileName(file.getOriginalFilename());
        Path filePath = Paths.get(UPLOAD_DIR, uniqueFileName);

        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        User currentUser = getCurrentUser();

        Video video = Video.builder()
                .title(title.trim())
                .originalFileName(uniqueFileName)
                .originalFilePath(filePath.toString())
                .status(VideoStatus.PENDING)
                .user(currentUser)
                .batchId(batchId)
                .operationType(operationType)
                .build();

        Video savedVideo = videoRepository.save(video);

        ffmpegService.processGifConversion(savedVideo.getId());
    }

    public void uploadForCompression(MultipartFile file, String title, OperationType operationType) throws IOException {
        File directory = new File(UPLOAD_DIR);
        if (!directory.exists()) directory.mkdirs();

        String uniqueFileName = generateUniqueFileName(file.getOriginalFilename());
        Path filePath = Paths.get(UPLOAD_DIR, uniqueFileName);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        User currentUser = getCurrentUser();

        Video video = Video.builder()
                .title(title.trim())
                .originalFileName(uniqueFileName)
                .originalFilePath(filePath.toString())
                .status(VideoStatus.PENDING)
                .user(currentUser)
                .operationType(operationType)
                .build();

        Video savedVideo = videoRepository.save(video);

        ffmpegService.processVideoCompression(savedVideo.getId());
    }

    public Video getOwnedVideo(UUID videoId) {
        String currentUserEmail = SecurityContextHolder.getContext().getAuthentication().getName();

        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new BusinessException("Video bulunamadı.", HttpStatus.NOT_FOUND));

        if (!video.getUser().getEmail().equals(currentUserEmail)) {
            throw new BusinessException("Güvenlik İhlali: Bu video size ait değil!", HttpStatus.FORBIDDEN);
        }

        return video;
    }

    private User getCurrentUser() {
        String userEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new BusinessException("Kullanıcı bulunamadı.", HttpStatus.NOT_FOUND));
    }

    private String generateUniqueFileName(String originalFilename) {
        String fileExtension = (originalFilename != null && originalFilename.contains("."))
                ? originalFilename.substring(originalFilename.lastIndexOf("."))
                : ".mp4";
        return UUID.randomUUID() + fileExtension;
    }
}
