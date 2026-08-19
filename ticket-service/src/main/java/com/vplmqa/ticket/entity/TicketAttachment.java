package com.vplmqa.ticket.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ticket_attachments")
public class TicketAttachment {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "ticket_id", nullable = false) private Ticket ticket;
    @Column(name = "file_name", nullable = false) private String fileName;
    @Column(name = "storage_url", nullable = false) private String storageUrl;
    @Column(name = "content_type", nullable = false) private String contentType;
    @CreationTimestamp @Column(name = "uploaded_at", nullable = false, updatable = false) private Instant uploadedAt;
    public UUID getId(){return id;} public void setId(UUID id){this.id=id;}
    public Ticket getTicket(){return ticket;} public void setTicket(Ticket ticket){this.ticket=ticket;}
    public String getFileName(){return fileName;} public void setFileName(String fileName){this.fileName=fileName;}
    public String getStorageUrl(){return storageUrl;} public void setStorageUrl(String storageUrl){this.storageUrl=storageUrl;}
    public String getContentType(){return contentType;} public void setContentType(String contentType){this.contentType=contentType;}
    public Instant getUploadedAt(){return uploadedAt;} public void setUploadedAt(Instant uploadedAt){this.uploadedAt=uploadedAt;}
}
