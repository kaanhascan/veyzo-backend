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
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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


        String originalFilename = file.getOriginalFilename();
        String fileExtension = originalFilename != null && originalFilename.contains(".")
                ? originalFilename.substring(originalFilename.lastIndexOf("."))
                : ".mp4";
        String uniqueFileName = UUID.randomUUID().toString() + fileExtension;
        Path filePath = Paths.get(UPLOAD_DIR, uniqueFileName);


        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);


        String userEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new BusinessException("Kullanıcı bulunamadı.", HttpStatus.NOT_FOUND));


        Video video = Video.builder()
                .title(title)
                .originalFileName(uniqueFileName)
                .originalFilePath(filePath.toString())
                .status(VideoStatus.PENDING)
                .user(currentUser)
                .batchId(batchId)
                .operationType(operationType)
                .build();

        Video savedVideo = videoRepository.save(video);

        ffmpegService.processVideoTrimming(savedVideo.getId(), startTime, duration);


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

        String userEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new BusinessException("Kullanıcı bulunamadı.", HttpStatus.NOT_FOUND));


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
        String userEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new BusinessException("Kullanıcı bulunamadı.", HttpStatus.NOT_FOUND));

        Video video = getOwnedVideo(videoId);

        String currentUserIdStr = currentUser.getId().toString();
        String videoOwnerIdStr = video.getUser().getId().toString();

        if(!videoOwnerIdStr.equals(currentUserIdStr)) {
            throw new BusinessException("Bu video başka bir kullanıcıya ait!", HttpStatus.FORBIDDEN);
        }

        try{
            if(video.getOriginalFilePath() != null){
                Files.deleteIfExists(Paths.get(video.getOriginalFilePath()));
            }
            if(video.getProcessedFilePath() != null){
                Files.deleteIfExists(Paths.get(video.getProcessedFilePath()));
            }
        }
        catch(Exception e){
            System.err.println("Dosya fiziksel olarak silinirken bir hata ile karşılaşıldı." + e.getMessage());
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
                String statusStr = video.getStatus() != null ? String.valueOf(video.getStatus()) : "";

                System.out.println("--- VİDEO İŞLENİYOR: " + video.getTitle() + " ---");

                if (!"COMPLETED".equalsIgnoreCase(statusStr) || video.getProcessedFilePath() == null) {
                    System.out.println("-> Atlandı: Statü uygun değil.");
                    continue;
                }

                Path filePath = Paths.get(video.getProcessedFilePath());

                if (Files.exists(filePath)) {
                    System.out.println("-> ZIP'e ekleniyor: " + video.getTitle());
                    String safeTitle = video.getTitle().replaceAll("[^a-zA-Z0-9_-]", "_");
                    String entryName = safeTitle + "_" + video.getId().toString().substring(0, 5) + ".mp4";
                    ZipEntry zipEntry = new ZipEntry(entryName);
                    zos.putNextEntry(zipEntry);
                    Files.copy(filePath, zos);
                    zos.closeEntry();
                    hasFiles = true;
                } else {
                    System.out.println("-> Dosya fiziksel olarak bulunamadı: " + filePath.toAbsolutePath());
                }
            }

            if (!hasFiles) {
                ZipEntry errorEntry = new ZipEntry("bilgi.txt");
                zos.putNextEntry(errorEntry);
                zos.write("Bu klasördeki videolar henuz islenmemis, basarisiz olmus veya silinmis olabilir.".getBytes());
                zos.closeEntry();
            }

        } catch (Exception e) {
            System.err.println("ZIP oluşturulurken hata: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void uploadForAudio(MultipartFile file, String title, String batchId,OperationType operationType) throws IOException {
        File directory = new File(UPLOAD_DIR);
        if (!directory.exists()) {
            directory.mkdirs();
        }

        String originalFilename = file.getOriginalFilename();
        String fileExtension = originalFilename != null && originalFilename.contains(".")
                ? originalFilename.substring(originalFilename.lastIndexOf("."))
                : ".mp4";
        String uniqueFileName = UUID.randomUUID().toString() + fileExtension;
        Path filePath = Paths.get(UPLOAD_DIR, uniqueFileName);

        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        String userEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new BusinessException("Kullanıcı bulunamadı.", HttpStatus.NOT_FOUND));

        Video video = Video.builder()
                .title(title)
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

    public void mergeVideos(MultipartFile[] newFiles, List<UUID> existingVideoIds, String title, String batchId,OperationType operationType) throws IOException {

        int newFilesCount = (newFiles != null) ? newFiles.length : 0;
        int existingCount = (existingVideoIds != null) ? existingVideoIds.size() : 0;

        if (newFilesCount + existingCount < 2) {
            throw new IllegalArgumentException("Birleştirme işlemi için en az 2 video (yeni veya mevcut) seçmelisiniz.");
        }
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("Geçerli bir başlık girmelisiniz.");
        }

        String userEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new BusinessException("Kullanıcı bulunamadı.", HttpStatus.NOT_FOUND));

        List<UUID> allVideoIdsToMerge = new ArrayList<>();

        if (existingVideoIds != null && !existingVideoIds.isEmpty()) {
            List<Video> sourceVideos = videoRepository.findAllById(existingVideoIds);
            for (Video v : sourceVideos) {
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
                if (file.isEmpty()) continue;

                String originalFilename = file.getOriginalFilename();
                String ext = (originalFilename != null && originalFilename.contains("."))
                        ? originalFilename.substring(originalFilename.lastIndexOf("."))
                        : ".mp4";
                String uniqueFileName = UUID.randomUUID().toString() + ext;
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

    public void uploadForGif(MultipartFile file, String title, String batchId,OperationType operationType) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Lütfen geçerli bir dosya yükleyin. Dosya boş olamaz.");
        }
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("GIF için bir başlık girmelisiniz.");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("video/")) {
            throw new IllegalArgumentException("Sadece video dosyaları GIF'e dönüştürülebilir.");
        }

        File directory = new File(UPLOAD_DIR);
        if (!directory.exists()) directory.mkdirs();

        String originalFilename = file.getOriginalFilename();
        String fileExtension = (originalFilename != null && originalFilename.contains("."))
                ? originalFilename.substring(originalFilename.lastIndexOf("."))
                : ".mp4";

        String uniqueFileName = UUID.randomUUID().toString() + fileExtension;
        Path filePath = Paths.get(UPLOAD_DIR, uniqueFileName);

        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        String userEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new BusinessException("Kullanıcı bulunamadı.", HttpStatus.NOT_FOUND));

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

    public void uploadForCompression(MultipartFile file, String title,OperationType operationType) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Lütfen geçerli bir dosya yükleyin.");
        }

        File directory = new File(UPLOAD_DIR);
        if (!directory.exists()) directory.mkdirs();

        String originalFilename = file.getOriginalFilename();
        String fileExtension = (originalFilename != null && originalFilename.contains("."))
                ? originalFilename.substring(originalFilename.lastIndexOf("."))
                : ".mp4";

        String uniqueFileName = UUID.randomUUID().toString() + fileExtension;
        Path filePath = Paths.get(UPLOAD_DIR, uniqueFileName);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        String userEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new BusinessException("Kullanıcı bulunamadı.", HttpStatus.NOT_FOUND));

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


}
