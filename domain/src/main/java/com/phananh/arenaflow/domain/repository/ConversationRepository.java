package com.phananh.arenaflow.domain.repository;

import com.phananh.arenaflow.domain.entity.chat.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {
}
