package com.phananh.ArenaFlow.entity.team;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Entity đại diện cho team_tag (tags/skill level của đội)
 */
@Entity
@Table(name = "team_tag")
public class TeamTag {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    @Column(name = "team_id", nullable = false)
    private UUID teamId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tag", nullable = false, length = 50)
    private Tag tag;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    // Getters and Setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTeamId() {
        return teamId;
    }

    public void setTeamId(UUID teamId) {
        this.teamId = teamId;
    }

    public Tag getTag() {
        return tag;
    }

    public void setTag(Tag tag) {
        this.tag = tag;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public enum Tag {
        Chill,
        Nhiệt_tình,
        Máu_lửa,
        Vui_chơi,
        Rèn_luyện,
        Thi_đấu,
        Mới_bắt_đầu,
        Đã_có_kinh_nghiệm,
        Lão_làng
    }
}
