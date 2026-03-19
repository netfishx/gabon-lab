package lab.gabon.model.entity;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("task_progress")
public record TaskProgress(
    @Id Long id,
    long customerId,
    long taskId,
    int currentCount,
    int targetCount,
    String periodKey,
    short taskStatus,
    int rewardDiamonds,
    Instant completedAt,
    Instant claimedAt,
    Instant createdAt,
    Instant updatedAt) {}
