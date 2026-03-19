package lab.gabon.model.entity;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("customer_sign_in_records")
public record CustomerSignInRecord(
    @Id Long id, long customerId, String periodKey, int rewardDiamonds, Instant createdAt) {}
