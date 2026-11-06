package com.phananh.arenaflow.domain.repository;

import com.phananh.arenaflow.domain.entity.user.OAuthAccount;
import com.phananh.arenaflow.domain.projection.UserAuthView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OAuthAccountRepository extends JpaRepository<OAuthAccount, UUID> {

    /** Bọc {@link #findAuthViewByProviderName}: cột provider lưu tên enum (chữ thường: google/facebook/phone). */
    default Optional<UserAuthView> findAuthView(OAuthAccount.Provider provider, String providerUserId) {
        return findAuthViewByProviderName(provider.name(), providerUserId);
    }

    /**
     * Một câu join user cho luồng đăng nhập. KHÔNG lọc user đã xoá: trả cờ {@code deleted} để caller báo AUTH_003 thay
     * vì coi như chưa đăng ký và tạo tài khoản mới.
     */
    @Query(value = """
            SELECT u.id AS userId, u.role AS role, (u.deleted_at IS NOT NULL) AS deleted
            FROM oauth_account a
            JOIN "user" u ON u.id = a.user_id
            WHERE a.provider = :provider AND a.provider_user_id = :providerUserId
            """, nativeQuery = true)
    Optional<UserAuthView> findAuthViewByProviderName(@Param("provider") String provider,
                                                      @Param("providerUserId") String providerUserId);

    List<OAuthAccount> findByUserId(UUID userId);
}
