package com.theguy.app.repository;

import com.theguy.app.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MessageRepository extends JpaRepository<Message, UUID> {
    List<Message> findByRoomIdOrderByCreatedAtAsc(UUID roomId);
    List<Message> findByRoomIdAndIsReadFalse(UUID roomId);
    long countByRoomIdAndIsReadFalse(UUID roomId);
}