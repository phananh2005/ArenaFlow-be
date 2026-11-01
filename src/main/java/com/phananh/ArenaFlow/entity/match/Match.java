package com.phananh.ArenaFlow.entity.match;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Entity đại diện cho match (bài đăng ghép kèo)
 */
@Entity
@Table(name = "match")
public class Match {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private Type type;

    @Column(name = "author_team_id", nullable = false)
    private UUID authorTeamId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status;

    @Column(name = "sport_id", nullable = false)
    private UUID sportId;

    @Column(name = "format", length = 10)
    private String format;

    @Column(name = "age_note", columnDefinition = "TEXT")
    private String ageNote;

    @Column(name = "match_nature", columnDefinition = "TEXT")
    private String matchNature;

    @Column(name = "venue_id")
    private UUID venueId;

    @Column(name = "match_start_time", nullable = false)
    private OffsetDateTime matchStartTime;

    @Column(name = "match_end_time", nullable = false)
    private OffsetDateTime matchEndTime;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "search_lat", precision = 10, scale = 7)
    private BigDecimal searchLat;

    @Column(name = "search_lng", precision = 10, scale = 7)
    private BigDecimal searchLng;

    @Column(name = "search_radius_km", precision = 5, scale = 2)
    private BigDecimal searchRadiusKm;

    @Column(name = "cost_type", length = 50)
    private String costType;

    @Column(name = "cost_split", length = 50)
    private String costSplit;

    @Column(name = "cost_venue_amount")
    private Long costVenueAmount;

    @Column(name = "cost_drinks_amount")
    private Long costDrinksAmount;

    @Column(name = "cost_deposit_holder", length = 100)
    private String costDepositHolder;

    @Column(name = "confirmed_opponent_team_id")
    private UUID confirmedOpponentTeamId;

    @Column(name = "confirmed_venue_id")
    private UUID confirmedVenueId;

    @Column(name = "confirmed_match_time")
    private OffsetDateTime confirmedMatchTime;

    @Column(name = "cancelled_by_team_id")
    private UUID cancelledByTeamId;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    // Getters and Setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public UUID getAuthorTeamId() {
        return authorTeamId;
    }

    public void setAuthorTeamId(UUID authorTeamId) {
        this.authorTeamId = authorTeamId;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public UUID getSportId() {
        return sportId;
    }

    public void setSportId(UUID sportId) {
        this.sportId = sportId;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getAgeNote() {
        return ageNote;
    }

    public void setAgeNote(String ageNote) {
        this.ageNote = ageNote;
    }

    public String getMatchNature() {
        return matchNature;
    }

    public void setMatchNature(String matchNature) {
        this.matchNature = matchNature;
    }

    public UUID getVenueId() {
        return venueId;
    }

    public void setVenueId(UUID venueId) {
        this.venueId = venueId;
    }

    public OffsetDateTime getMatchStartTime() {
        return matchStartTime;
    }

    public void setMatchStartTime(OffsetDateTime matchStartTime) {
        this.matchStartTime = matchStartTime;
    }

    public OffsetDateTime getMatchEndTime() {
        return matchEndTime;
    }

    public void setMatchEndTime(OffsetDateTime matchEndTime) {
        this.matchEndTime = matchEndTime;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(OffsetDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public BigDecimal getSearchLat() {
        return searchLat;
    }

    public void setSearchLat(BigDecimal searchLat) {
        this.searchLat = searchLat;
    }

    public BigDecimal getSearchLng() {
        return searchLng;
    }

    public void setSearchLng(BigDecimal searchLng) {
        this.searchLng = searchLng;
    }

    public BigDecimal getSearchRadiusKm() {
        return searchRadiusKm;
    }

    public void setSearchRadiusKm(BigDecimal searchRadiusKm) {
        this.searchRadiusKm = searchRadiusKm;
    }

    public String getCostType() {
        return costType;
    }

    public void setCostType(String costType) {
        this.costType = costType;
    }

    public String getCostSplit() {
        return costSplit;
    }

    public void setCostSplit(String costSplit) {
        this.costSplit = costSplit;
    }

    public Long getCostVenueAmount() {
        return costVenueAmount;
    }

    public void setCostVenueAmount(Long costVenueAmount) {
        this.costVenueAmount = costVenueAmount;
    }

    public Long getCostDrinksAmount() {
        return costDrinksAmount;
    }

    public void setCostDrinksAmount(Long costDrinksAmount) {
        this.costDrinksAmount = costDrinksAmount;
    }

    public String getCostDepositHolder() {
        return costDepositHolder;
    }

    public void setCostDepositHolder(String costDepositHolder) {
        this.costDepositHolder = costDepositHolder;
    }

    public UUID getConfirmedOpponentTeamId() {
        return confirmedOpponentTeamId;
    }

    public void setConfirmedOpponentTeamId(UUID confirmedOpponentTeamId) {
        this.confirmedOpponentTeamId = confirmedOpponentTeamId;
    }

    public UUID getConfirmedVenueId() {
        return confirmedVenueId;
    }

    public void setConfirmedVenueId(UUID confirmedVenueId) {
        this.confirmedVenueId = confirmedVenueId;
    }

    public OffsetDateTime getConfirmedMatchTime() {
        return confirmedMatchTime;
    }

    public void setConfirmedMatchTime(OffsetDateTime confirmedMatchTime) {
        this.confirmedMatchTime = confirmedMatchTime;
    }

    public UUID getCancelledByTeamId() {
        return cancelledByTeamId;
    }

    public void setCancelledByTeamId(UUID cancelledByTeamId) {
        this.cancelledByTeamId = cancelledByTeamId;
    }

    public OffsetDateTime getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(OffsetDateTime cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public enum Type {
        HAS_VENUE, NEED_VENUE
    }

    public enum Status {
        OPEN, CONFIRMED, ARCHIVED, COMPLETED, EXPIRED, CANCELLED
    }
}
