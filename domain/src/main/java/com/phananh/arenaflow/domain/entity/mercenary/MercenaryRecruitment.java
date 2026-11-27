package com.phananh.arenaflow.domain.entity.mercenary;

import com.phananh.arenaflow.common.time.AppTime;
import com.phananh.arenaflow.domain.entity.converter.PositionConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.UUID;

/**
 * Tin tuyển mercenary của một đội cho một trận — kèo trên hệ thống ({@code matchId}) hoặc trận ngoài hệ thống
 * ({@code externalMatchId}). Trạng thái và số lượng chỉ đổi bằng câu UPDATE có điều kiện ({@code updatable = false});
 * entity chỉ dùng để đọc.
 */
@Entity
@Table(name = "mercenary_recruitment")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class MercenaryRecruitment {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "match_id", updatable = false)
    private UUID matchId;

    @Column(name = "external_match_id", updatable = false)
    private UUID externalMatchId;

    @Column(name = "team_id", nullable = false, updatable = false)
    private UUID teamId;

    @Convert(converter = PositionConverter.class)
    @Column(name = "position", nullable = false, length = 50, updatable = false)
    private Position position;

    @Column(name = "quantity_needed", nullable = false, updatable = false)
    private Integer quantityNeeded;

    @Column(name = "quantity_approved", nullable = false, updatable = false)
    @Builder.Default
    private Integer quantityApproved = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20, updatable = false)
    @Builder.Default
    private Status status = Status.OPEN;

    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    private UUID createdByUserId;

    @Column(name = "closed_at", updatable = false)
    private LocalDateTime closedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @Setter(AccessLevel.NONE)
    private LocalDateTime updatedAt;

    /** Gán {@code createdAt} và {@code updatedAt} = giờ hiện tại ({@link AppTime#now()}) khi lưu lần đầu. */
    @PrePersist
    protected void onCreate() {
        createdAt = AppTime.now();
        updatedAt = createdAt;
    }

    /** Cập nhật {@code updatedAt} = giờ hiện tại ({@link AppTime#now()}) mỗi lần entity được sửa. */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = AppTime.now();
    }

    /** Vị trí; lưu và trả API bằng nhãn tiếng Việt. */
    public enum Position {
        GOALKEEPER("Thủ môn"), DEFENDER("Hậu vệ"), MIDFIELDER("Tiền vệ"), FORWARD("Tiền đạo");

        private final String label;

        Position(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        /** Nhãn tiếng Việt → enum; nhãn lạ → IllegalArgumentException. */
        public static Position fromLabel(String label) {
            return Arrays.stream(values()).filter(p -> p.label.equals(label)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown position label"));
        }
    }

    public enum Status {
        OPEN, FILLED, EXPIRED, CLOSED
    }
}
