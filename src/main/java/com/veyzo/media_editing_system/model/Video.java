package com.veyzo.media_editing_system.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "videos")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Video {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(unique = true, nullable = false)
    private UUID id;


    private String title;


    private String originalFileName;
    private String originalFilePath;


    private String processedFileName;
    private String processedFilePath;


    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VideoStatus status;

    @Enumerated(EnumType.STRING)
    private OperationType operationType;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "batch_id")
    private String batchId;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}