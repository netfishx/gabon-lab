package lab.gabon.model.entity;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("video_play_records")
public record VideoPlayRecord(
    @Id Long id,
    long videoId,
    Long customerId,
    short playType,
    String ipAddress,
    Instant createdAt) {}
