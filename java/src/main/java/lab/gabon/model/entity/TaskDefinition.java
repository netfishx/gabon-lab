package lab.gabon.model.entity;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("task_definitions")
public record TaskDefinition(
    @Id Long id,
    String taskCode,
    String taskName,
    String description,
    short taskType,
    short taskCategory,
    int targetCount,
    int rewardDiamonds,
    String iconUrl,
    int displayOrder,
    boolean vipOnly,
    short status,
    Instant startTime,
    Instant endTime,
    Instant createdAt,
    Instant updatedAt) {}
