package com.veyzo.media_editing_system.dto.response;

import com.veyzo.media_editing_system.model.VideoStatus;

public record VideoStatusResponse(
        VideoStatus status,
        String processedFileName

) {
}
