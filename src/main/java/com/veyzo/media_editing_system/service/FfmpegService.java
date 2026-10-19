package com.veyzo.media_editing_system.service;

import com.veyzo.media_editing_system.model.Video;
import com.veyzo.media_editing_system.model.VideoStatus;
import com.veyzo.media_editing_system.repository.VideoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FfmpegService {

    private final VideoRepository videoRepository;
    private final String PROCESSED_DIR = System.getProperty("user.dir") + "/uploads/processed/";

    @Async
    public void processVideoTrimming(UUID videoId, String startTime, String duration) {


        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new RuntimeException("Video bulunamadı"));

        try {
            video.setStatus(VideoStatus.PROCESSING);
            videoRepository.save(video);

            File directory = new File(PROCESSED_DIR);
            if (!directory.exists()) directory.mkdirs();

            String outputFileName = "trimmed_" + video.getOriginalFileName();
            String outputFilePath = PROCESSED_DIR + outputFileName;


            ProcessBuilder processBuilder = new ProcessBuilder(
                    "ffmpeg", "-y",
                    "-i", video.getOriginalFilePath(),
                    "-ss", startTime,
                    "-t", duration,
                    "-c", "copy",
                    outputFilePath
            );


            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();


            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("[FFMPEG]: " + line);
            }


            int exitCode = process.waitFor();

            if (exitCode == 0) {
                video.setStatus(VideoStatus.COMPLETED);
                video.setProcessedFileName(outputFileName);
                video.setProcessedFilePath(outputFilePath);
            } else {
                video.setStatus(VideoStatus.FAILED);
            }

        } catch (Exception e) {
            e.printStackTrace();
            video.setStatus(VideoStatus.FAILED);
        } finally {
            videoRepository.save(video);
        }
    }


    @Async
    public void processAudioExtraction(UUID videoId) {

        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new RuntimeException("Video bulunamadı"));

        try {
            video.setStatus(VideoStatus.PROCESSING);
            videoRepository.save(video);

            File directory = new File(PROCESSED_DIR);
            if (!directory.exists()) directory.mkdirs();

            String outputFileName = "audio_" + UUID.randomUUID().toString() + ".mp3";
            String outputFilePath = PROCESSED_DIR + outputFileName;

            ProcessBuilder processBuilder = new ProcessBuilder(
                    "ffmpeg", "-y",
                    "-i", video.getOriginalFilePath(),
                    "-vn",
                    "-acodec", "libmp3lame",
                    "-q:a", "2",
                    outputFilePath
            );

            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("[FFMPEG AUDIO]: " + line);
            }

            int exitCode = process.waitFor();

            if (exitCode == 0) {
                video.setStatus(VideoStatus.COMPLETED);
                video.setProcessedFileName(outputFileName);
                video.setProcessedFilePath(outputFilePath);
            } else {
                video.setStatus(VideoStatus.FAILED);
            }

        } catch (Exception e) {
            e.printStackTrace();
            video.setStatus(VideoStatus.FAILED);
        } finally {
            videoRepository.save(video);
        }
    }

    @Async
    public void processVideoMerging(UUID mergedVideoId, List<UUID> sourceVideoIds) {
        Video mergedVideo = videoRepository.findById(mergedVideoId)
                .orElseThrow(() -> new RuntimeException("Birleştirilecek video kaydı bulunamadı"));

        try {
            mergedVideo.setStatus(VideoStatus.PROCESSING);
            videoRepository.save(mergedVideo);

            List<Video> sourceVideos = videoRepository.findAllById(sourceVideoIds);

            File directory = new File(PROCESSED_DIR);
            if (!directory.exists()) directory.mkdirs();

            String outputFileName = "merged_" + UUID.randomUUID().toString() + ".mp4";
            String outputFilePath = PROCESSED_DIR + outputFileName;

            File listFile = new File(PROCESSED_DIR + "concat_" + mergedVideo.getId() + ".txt");
            try (java.io.PrintWriter pw = new java.io.PrintWriter(listFile)) {
                for (Video v : sourceVideos) {
                    pw.println("file '" + v.getProcessedFilePath().replace("\\", "/") + "'");
                }
            }

            ProcessBuilder processBuilder = new ProcessBuilder(
                    "ffmpeg", "-y",
                    "-f", "concat",
                    "-safe", "0",
                    "-i", listFile.getAbsolutePath(),
                    "-c:v", "libx264",
                    "-threads", "2",
                    "-c:a", "aac",
                    "-preset", "fast",
                    outputFilePath
            );

            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("[FFMPEG]: " + line);
            }
            int exitCode = process.waitFor();

            listFile.delete();

            if (exitCode == 0) {
                mergedVideo.setStatus(VideoStatus.COMPLETED);
                mergedVideo.setProcessedFileName(outputFileName);
                mergedVideo.setProcessedFilePath(outputFilePath);
            } else {
                mergedVideo.setStatus(VideoStatus.FAILED);
            }
        } catch (Exception e) {
            e.printStackTrace();
            mergedVideo.setStatus(VideoStatus.FAILED);
        } finally {
            videoRepository.save(mergedVideo);
        }
    }

    @Async
    public void processGifConversion(UUID videoId) {
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new RuntimeException("GIF için video kaydı bulunamadı"));

        try {
            video.setStatus(VideoStatus.PROCESSING);
            videoRepository.save(video);

            File directory = new File(PROCESSED_DIR);
            if (!directory.exists()) directory.mkdirs();

            String outputFileName = "gif_" + UUID.randomUUID().toString() + ".gif";
            String outputFilePath = PROCESSED_DIR + outputFileName;

            ProcessBuilder processBuilder = new ProcessBuilder(
                    "ffmpeg", "-y",
                    "-i", video.getOriginalFilePath(),
                    "-vf", "fps=10,scale=480:-1:flags=lanczos",
                    outputFilePath
            );

            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                // System.out.println("[FFMPEG GIF]: " + line);
            }

            int exitCode = process.waitFor();

            if (exitCode == 0) {
                video.setStatus(VideoStatus.COMPLETED);
                video.setProcessedFileName(outputFileName);
                video.setProcessedFilePath(outputFilePath);
            } else {
                video.setStatus(VideoStatus.FAILED);
            }
        } catch (Exception e) {
            e.printStackTrace();
            video.setStatus(VideoStatus.FAILED);
        } finally {
            videoRepository.save(video);
        }
    }

    @Async
    public void processVideoCompression(UUID videoId) {
        Video initialVideo = videoRepository.findById(videoId)
                .orElseThrow(() -> new RuntimeException("Sıkıştırma için video kaydı bulunamadı"));

        initialVideo.setStatus(VideoStatus.PROCESSING);
        videoRepository.save(initialVideo);

        boolean isSuccess = false;
        String finalOutputFileName = null;
        String finalOutputFilePath = null;

        try {
            File directory = new File(PROCESSED_DIR);
            if (!directory.exists()) directory.mkdirs();

            String outputFileName = "compressed_" + UUID.randomUUID().toString() + ".mp4";
            String outputFilePath = PROCESSED_DIR + outputFileName;

            ProcessBuilder processBuilder = new ProcessBuilder(
                    "ffmpeg", "-y",
                    "-i", initialVideo.getOriginalFilePath(),
                    "-vcodec", "libx264",
                    "-crf", "28",
                    "-preset", "fast",
                    "-c:a", "aac",
                    "-b:a", "128k",
                    outputFilePath
            );

            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                // Buffer temizliği...
            }

            int exitCode = process.waitFor();

            if (exitCode == 0) {
                isSuccess = true;
                finalOutputFileName = outputFileName;
                finalOutputFilePath = outputFilePath;
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            java.util.Optional<Video> optionalVideo = videoRepository.findById(videoId);

            if (optionalVideo.isPresent()) {
                Video latestVideo = optionalVideo.get();

                if (isSuccess) {
                    latestVideo.setStatus(VideoStatus.COMPLETED);
                    latestVideo.setProcessedFileName(finalOutputFileName);
                    latestVideo.setProcessedFilePath(finalOutputFilePath);
                } else {
                    latestVideo.setStatus(VideoStatus.FAILED);
                }

                videoRepository.save(latestVideo);
            }
        }
    }
}
