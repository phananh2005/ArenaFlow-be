package com.phananh.ArenaFlow.repository;

import com.phananh.ArenaFlow.entity.user.OAuthAccount;
import com.phananh.ArenaFlow.repository.projection.UserAuthView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OAuthAccountRepository extends JpaRepository<OAuthAccount, UUID> {

    default Optional<UserAuthView> findAuthView(OAuthAccount.Provider provider, String providerUserId) {
        return findAuthViewByProviderName(provider.name(), providerUserId);
    }

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
