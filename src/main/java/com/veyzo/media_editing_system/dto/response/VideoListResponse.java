package com.veyzo.media_editing_system.dto.response;

import com.veyzo.media_editing_system.model.OperationType;
import com.veyzo.media_editing_system.model.VideoStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record VideoListResponse(
        UUID id,
        String title,
        VideoStatus status,
        LocalDateTime createdAt,
        String batchId,
        String originalFileName,
        String processedFileName,
        OperationType operationType
) {
}
