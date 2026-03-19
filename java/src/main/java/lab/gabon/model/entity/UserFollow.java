package lab.gabon.model.entity;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("user_follows")
public record UserFollow(@Id Long id, long followerId, long followedId, Instant createdAt) {}
