package com.vplmqa.notification.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "retry_queue")
public class RetryQueue {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "delivery_log_id", nullable = false) private DeliveryLog deliveryLog;
    @Column(name = "next_attempt_at", nullable = false) private Instant nextAttemptAt;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    public UUID getId(){return id;} public void setId(UUID id){this.id=id;}
    public DeliveryLog getDeliveryLog(){return deliveryLog;} public void setDeliveryLog(DeliveryLog deliveryLog){this.deliveryLog=deliveryLog;}
    public Instant getNextAttemptAt(){return nextAttemptAt;} public void setNextAttemptAt(Instant nextAttemptAt){this.nextAttemptAt=nextAttemptAt;}
    public Instant getCreatedAt(){return createdAt;} public void setCreatedAt(Instant createdAt){this.createdAt=createdAt;}
}
