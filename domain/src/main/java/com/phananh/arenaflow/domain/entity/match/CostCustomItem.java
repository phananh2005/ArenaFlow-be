package com.phananh.arenaflow.domain.entity.match;

import com.phananh.arenaflow.common.time.AppTime;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity đại diện cho cost_custom_item (khoản phí tự nhập). Thuộc đúng một chủ: kèo (luồng A, kèo B đã ghép) hoặc
 * thương lượng luồng B. Ghi bằng câu native trong repository (id do DB sinh); entity dùng để đọc.
 */
@Entity
@Table(name = "cost_custom_item")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class CostCustomItem {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "match_id", nullable = true)
    private UUID matchId;

    @Column(name = "pairing_id")
    private UUID pairingId;

    @Column(name = "item_name", nullable = false, length = 100)
    private String itemName;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private LocalDateTime createdAt;

    /** Gán {@code createdAt} = giờ hiện tại ({@link AppTime#now()}) khi lưu lần đầu. */
    @PrePersist
    protected void onCreate() {
        createdAt = AppTime.now();
    }
}
