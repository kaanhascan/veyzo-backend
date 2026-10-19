package com.veyzo.media_editing_system.repository;

import com.veyzo.media_editing_system.model.User;
import com.veyzo.media_editing_system.model.Video;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface VideoRepository extends JpaRepository<Video, UUID> {

    List<Video> findByUser_IdOrderByCreatedAtDesc(UUID userId);
    List<Video> findByBatchIdAndUser(String batchId, User user);
}
