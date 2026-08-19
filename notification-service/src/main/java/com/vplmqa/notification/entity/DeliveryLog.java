package com.vplmqa.notification.entity;

import com.vplmqa.notification.enumtype.ChannelEnum;
import com.vplmqa.notification.enumtype.DeliveryStatusEnum;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "delivery_logs")
public class DeliveryLog {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private ChannelEnum channel;
    @Column(nullable = false) private String subject;
    @Column(columnDefinition = "TEXT") private String body;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private DeliveryStatusEnum status;
    @CreationTimestamp @Column(name = "sent_at", nullable = false, updatable = false) private Instant sentAt;
    @Column(name = "failure_reason") private String failureReason;
    @Column(nullable = false) private int attempts;
    public UUID getId(){return id;} public void setId(UUID id){this.id=id;}
    public UUID getUserId(){return userId;} public void setUserId(UUID userId){this.userId=userId;}
    public ChannelEnum getChannel(){return channel;} public void setChannel(ChannelEnum channel){this.channel=channel;}
    public String getSubject(){return subject;} public void setSubject(String subject){this.subject=subject;}
    public String getBody(){return body;} public void setBody(String body){this.body=body;}
    public DeliveryStatusEnum getStatus(){return status;} public void setStatus(DeliveryStatusEnum status){this.status=status;}
    public Instant getSentAt(){return sentAt;} public void setSentAt(Instant sentAt){this.sentAt=sentAt;}
    public String getFailureReason(){return failureReason;} public void setFailureReason(String failureReason){this.failureReason=failureReason;}
    public int getAttempts(){return attempts;} public void setAttempts(int attempts){this.attempts=attempts;}
}
