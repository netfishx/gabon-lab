package lab.gabon.model.entity;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("video_likes")
public record VideoLike(@Id Long id, long videoId, long customerId, Instant createdAt) {}
