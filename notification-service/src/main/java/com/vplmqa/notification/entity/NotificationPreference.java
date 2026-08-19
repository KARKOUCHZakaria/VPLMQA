package com.vplmqa.notification.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_preferences")
public class NotificationPreference {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "project_id", nullable = false) private UUID projectId;
    @Column(name = "email_enabled", nullable = false) private boolean emailEnabled;
    @Column(name = "slack_enabled", nullable = false) private boolean slackEnabled;
    @Column(name = "slack_webhook") private String slackWebhook;
    @Column(name = "email_address") private String emailAddress;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    public UUID getId(){return id;} public void setId(UUID id){this.id=id;}
    public UUID getUserId(){return userId;} public void setUserId(UUID userId){this.userId=userId;}
    public UUID getProjectId(){return projectId;} public void setProjectId(UUID projectId){this.projectId=projectId;}
    public boolean isEmailEnabled(){return emailEnabled;} public void setEmailEnabled(boolean emailEnabled){this.emailEnabled=emailEnabled;}
    public boolean isSlackEnabled(){return slackEnabled;} public void setSlackEnabled(boolean slackEnabled){this.slackEnabled=slackEnabled;}
    public String getSlackWebhook(){return slackWebhook;} public void setSlackWebhook(String slackWebhook){this.slackWebhook=slackWebhook;}
    public String getEmailAddress(){return emailAddress;} public void setEmailAddress(String emailAddress){this.emailAddress=emailAddress;}
    public Instant getCreatedAt(){return createdAt;} public void setCreatedAt(Instant createdAt){this.createdAt=createdAt;}
    public Instant getUpdatedAt(){return updatedAt;} public void setUpdatedAt(Instant updatedAt){this.updatedAt=updatedAt;}
}
