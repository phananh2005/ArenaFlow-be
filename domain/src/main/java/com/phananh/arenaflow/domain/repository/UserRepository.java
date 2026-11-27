package com.phananh.arenaflow.domain.repository;

import com.phananh.arenaflow.domain.entity.user.User;
import com.phananh.arenaflow.domain.projection.InvitationCandidateView;
import com.phananh.arenaflow.domain.projection.ProfileView;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Truy cập bảng {@code "user"}: tìm user chưa xoá mềm, hồ sơ kèm địa giới, xoá mềm idempotent. */
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByIdAndDeletedAtIsNull(UUID id);

    /**
     * Hồ sơ + phường/xã + tỉnh trong một câu; user đã xoá mềm → empty. Không có ward → các field ward/province null.
     */
    @Query(value = """
            SELECT u.id AS userId, u.role AS role, u.phone_number AS phoneNumber,
                   u.display_name AS displayName, u.avatar_url AS avatarUrl, u.bio AS bio,
                   u.preferred_position AS preferredPosition, u.date_of_birth AS dateOfBirth,
                   u.gender AS gender,
                   w.id AS wardId, w.name AS wardName, p.id AS provinceId, p.name AS provinceName,
                   u.created_at AS createdAt, u.updated_at AS updatedAt
            FROM "user" u
            LEFT JOIN administrative_unit w ON w.id = u.ward_id
            LEFT JOIN administrative_unit p ON p.id = w.parent_id
            WHERE u.id = :id AND u.deleted_at IS NULL
            """, nativeQuery = true)
    Optional<ProfileView> findProfileViewById(@Param("id") UUID id);

    /** Chỉ xoá mềm user chưa xoá → gọi lại trả 0 (idempotent). */
    @Modifying
    @Query(value = """
            UPDATE "user" SET deleted_at = :now, updated_at = :now
            WHERE id = :id AND deleted_at IS NULL
            """, nativeQuery = true)
    int softDelete(@Param("id") UUID id, @Param("now") LocalDateTime now);

    boolean existsByIdAndDeletedAtIsNull(UUID id);

    /** Chuẩn hoá chuỗi tìm theo tên bằng đúng hàm của index (bỏ dấu, chữ thường, ký tự lạ → dấu cách); không đọc bảng. */
    @Query(value = "SELECT user_name_fold(:q)", nativeQuery = true)
    String foldName(@Param("q") String q);

    /**
     * Người có thể mời theo số điện thoại khớp chính xác (tối đa 1 người nhờ unique index số điện thoại của user chưa
     * xoá). Loại người đã xoá, Admin và chính người gọi; kèm trạng thái PENDING/ACTIVE với đội {@code teamId}.
     */
    @Query(value = """
            SELECT u.id AS userId, u.display_name AS displayName, u.avatar_url AS avatarUrl,
                   m.status AS membershipStatus
            FROM "user" u
            LEFT JOIN team_member m
                   ON m.team_id = :teamId AND m.user_id = u.id AND m.status IN ('PENDING', 'ACTIVE')
            WHERE u.phone_number = :phone
              AND u.deleted_at IS NULL
              AND u.role <> 'ADMIN'
              AND u.id <> :callerId
            ORDER BY u.id
            LIMIT :limit
            """, nativeQuery = true)
    List<InvitationCandidateView> searchCandidatesByPhone(@Param("teamId") UUID teamId,
                                                          @Param("callerId") UUID callerId,
                                                          @Param("phone") String phone,
                                                          @Param("limit") int limit);

    /**
     * Người có thể mời theo tên: mỗi từ của {@code tsQuery} ({@code w1:* & w2:*}, chỉ gồm [a-z0-9]) phải khớp đầu một
     * từ trong tên đã bỏ dấu — dùng index GIN biểu thức trên tên, nên vế @@ và điều kiện deleted_at phải giữ đúng như
     * index. Tên bắt đầu bằng {@code prefix} xếp trước, rồi theo tên (collation ICU), rồi id. Sắp xếp phải đọc mọi
     * dòng khớp nên thời gian tăng theo số người khớp: giới hạn câu 2 giây, hết giờ → QueryTimeoutException.
     * LEFT JOIN team_member cho tối đa 1 dòng mỗi user nhờ unique index PENDING/ACTIVE theo (team_id, user_id).
     */
    @QueryHints(@QueryHint(name = "jakarta.persistence.query.timeout", value = "2000"))
    @Query(value = """
            SELECT u.id AS userId, u.display_name AS displayName, u.avatar_url AS avatarUrl,
                   m.status AS membershipStatus
            FROM "user" u
            LEFT JOIN team_member m
                   ON m.team_id = :teamId AND m.user_id = u.id AND m.status IN ('PENDING', 'ACTIVE')
            WHERE user_name_search_vector(u.display_name) @@ to_tsquery('simple', :tsQuery)
              AND u.deleted_at IS NULL
              AND u.role <> 'ADMIN'
              AND u.id <> :callerId
            ORDER BY starts_with(user_name_fold(u.display_name), :prefix) DESC,
                     u.display_name COLLATE "vi-VN-x-icu", u.id
            LIMIT :limit
            """, nativeQuery = true)
    List<InvitationCandidateView> searchCandidatesByName(@Param("teamId") UUID teamId,
                                                         @Param("callerId") UUID callerId,
                                                         @Param("tsQuery") String tsQuery,
                                                         @Param("prefix") String prefix,
                                                         @Param("limit") int limit);
}
