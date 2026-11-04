package com.phananh.arenaflow.domain.repository;

import com.phananh.arenaflow.domain.entity.user.User;
import com.phananh.arenaflow.domain.projection.ProfileView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByIdAndDeletedAtIsNull(UUID id);

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
    int softDelete(@Param("id") UUID id, @Param("now") OffsetDateTime now);
}
