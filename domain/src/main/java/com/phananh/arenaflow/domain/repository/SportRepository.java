package com.phananh.arenaflow.domain.repository;

import com.phananh.arenaflow.domain.entity.match.Sport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SportRepository extends JpaRepository<Sport, UUID> {

    Page<Sport> findByIsActiveOrderByNameAscIdAsc(boolean isActive, Pageable pageable);

    Page<Sport> findAllByOrderByNameAscIdAsc(Pageable pageable);
}
