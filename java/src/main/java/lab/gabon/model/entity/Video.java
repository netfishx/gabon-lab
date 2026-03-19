package lab.gabon.model.entity;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("videos")
public record Video(
    @Id Long id,
    long customerId,
    String title,
    String description,
    String fileName,
    long fileSize,
    String fileUrl,
    String thumbnailUrl,
    String previewGifUrl,
    String mimeType,
    Integer duration,
    Integer width,
    Integer height,
    short status,
    String reviewNotes,
    Long reviewedBy,
    Instant reviewedAt,
    long totalClicks,
    long validClicks,
    long likeCount,
    Instant createdAt,
    Instant updatedAt,
    Instant deletedAt) {}
