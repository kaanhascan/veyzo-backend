package com.veyzo.media_editing_system.dto.response;

import com.veyzo.media_editing_system.model.VideoStatus;

import java.util.UUID;

public record UploadResponse(
        UUID videoId,
        String title,
        VideoStatus status,
        String message
) {
}
