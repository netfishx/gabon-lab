package lab.gabon.model.entity;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("customers")
public record Customer(
    @Id Long id,
    String username,
    String passwordHash,
    String name,
    String phone,
    String email,
    String avatarUrl,
    String signature,
    boolean isVip,
    long diamondBalance,
    String withdrawalPasswordHash,
    Instant lastLoginAt,
    Instant createdAt,
    Instant updatedAt,
    Instant deletedAt) {}
