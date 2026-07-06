package com.theguy.app.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.UUID;

@Entity
@Table(name = "chat_rooms")
@Data
@EqualsAndHashCode(callSuper = true)
public class ChatRoom extends BaseEntity {

    @Column(nullable = false)
    private UUID jobId;

    @Column(nullable = false)
    private UUID customerId;

    @Column(nullable = false)
    private UUID providerId;
}