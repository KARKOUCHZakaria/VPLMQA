package com.vplmqa.ticket.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ticket_comments")
public class TicketComment {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "ticket_id", nullable = false) private Ticket ticket;
    @Column(name = "author_id", nullable = false) private UUID authorId;
    @Column(columnDefinition = "TEXT", nullable = false) private String body;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    public UUID getId(){return id;} public void setId(UUID id){this.id=id;}
    public Ticket getTicket(){return ticket;} public void setTicket(Ticket ticket){this.ticket=ticket;}
    public UUID getAuthorId(){return authorId;} public void setAuthorId(UUID authorId){this.authorId=authorId;}
    public String getBody(){return body;} public void setBody(String body){this.body=body;}
    public Instant getCreatedAt(){return createdAt;} public void setCreatedAt(Instant createdAt){this.createdAt=createdAt;}
}
