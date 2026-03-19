package lab.gabon.model.entity;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("admin_users")
public record AdminUser(
    @Id Long id,
    String username,
    String passwordHash,
    short role,
    String fullName,
    String phone,
    String avatarUrl,
    short status,
    Instant lastLoginAt,
    Instant createdAt,
    Instant updatedAt,
    Instant deletedAt) {}
