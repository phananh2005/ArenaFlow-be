package com.phananh.arenaflow.domain.repository;

import com.phananh.arenaflow.domain.entity.user.OAuthAccount;
import com.phananh.arenaflow.domain.entity.user.User;
import com.phananh.arenaflow.domain.projection.ProfileView;
import com.phananh.arenaflow.domain.projection.UserAuthView;
import com.phananh.arenaflow.domain.projection.WardView;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserRepositoryTest extends RepositoryIntegrationTest {

    static final UUID HCM_PROVINCE = UUID.fromString("00000000-0000-7000-8000-000000000001");
    static final UUID BEN_NGHE_WARD = UUID.fromString("10000000-0000-7000-8000-000000000001");

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OAuthAccountRepository oauthAccountRepository;

    @Autowired
    private AdministrativeUnitRepository administrativeUnitRepository;

    @Test
    void generatesTimeOrderedUuidVersion7Ids() {
        User first = userRepository.save(newUser("Uuid First", null));
        User second = userRepository.save(newUser("Uuid Second", null));
        flushAndClear();

        assertThat(first.getId().version()).isEqualTo(7);
        assertThat(second.getId().version()).isEqualTo(7);
        assertThat(second.getId().toString()).isGreaterThan(first.getId().toString());
    }

    @Test
    void findsOnlyNotDeletedUserById() {
        User active = userRepository.save(newUser("Active", null));
        User deleted = newUser("Deleted", null);
        deleted.setDeletedAt(OffsetDateTime.now());
        userRepository.save(deleted);
        flushAndClear();

        assertThat(userRepository.findByIdAndDeletedAtIsNull(active.getId())).isPresent();
        assertThat(userRepository.findByIdAndDeletedAtIsNull(deleted.getId())).isEmpty();
    }

    @Test
    void findsProfileViewWithWardAndProvinceInOneQuery() {
        User user = newUser("Profile User", BEN_NGHE_WARD);
        user.setPhoneNumber("0900000000");
        userRepository.save(user);
        flushAndClear();
        Statistics statistics = resetStatistics();

        Optional<ProfileView> view = userRepository.findProfileViewById(user.getId());

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
        assertThat(view).hasValueSatisfying(profile -> {
            assertThat(profile.getUserId()).isEqualTo(user.getId());
            assertThat(profile.getRole()).isEqualTo("USER");
            assertThat(profile.getDisplayName()).isEqualTo("Profile User");
            assertThat(profile.getPhoneNumber()).isEqualTo("0900000000");
            assertThat(profile.getWardId()).isEqualTo(BEN_NGHE_WARD);
            assertThat(profile.getWardName()).isEqualTo("Phường Bến Nghé");
            assertThat(profile.getProvinceId()).isEqualTo(HCM_PROVINCE);
            assertThat(profile.getProvinceName()).isEqualTo("TP. Hồ Chí Minh");
            assertThat(profile.getCreatedAt()).isNotNull();
        });
    }

    @Test
    void profileViewHasNullWardWhenNotChosenAndIsEmptyForDeletedUser() {
        User noWard = userRepository.save(newUser("No Ward", null));
        User deleted = newUser("Deleted", null);
        deleted.setDeletedAt(OffsetDateTime.now());
        userRepository.save(deleted);
        flushAndClear();

        assertThat(userRepository.findProfileViewById(noWard.getId()))
                .hasValueSatisfying(profile -> assertThat(profile.getWardId()).isNull());
        assertThat(userRepository.findProfileViewById(deleted.getId())).isEmpty();
    }

    @Test
    void softDeleteOnlyUpdatesNotYetDeletedUser() {
        User user = userRepository.save(newUser("To Delete", null));
        flushAndClear();

        assertThat(userRepository.softDelete(user.getId(), OffsetDateTime.now())).isEqualTo(1);
        assertThat(userRepository.softDelete(user.getId(), OffsetDateTime.now())).isZero();
        flushAndClear();
        assertThat(userRepository.findByIdAndDeletedAtIsNull(user.getId())).isEmpty();
    }

    @Test
    void findsAuthViewByProviderWithDeletedFlagInOneQuery() {
        User user = userRepository.save(newUser("Oauth User", null));
        OAuthAccount account = OAuthAccount.builder()
                .userId(user.getId())
                .provider(OAuthAccount.Provider.google)
                .providerUserId("oauth-" + user.getId() + "@example.com")
                .build();
        oauthAccountRepository.save(account);
        flushAndClear();
        Statistics statistics = resetStatistics();

        Optional<UserAuthView> view = oauthAccountRepository.findAuthView(OAuthAccount.Provider.google, account.getProviderUserId());

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
        assertThat(view).hasValueSatisfying(auth -> {
            assertThat(auth.getUserId()).isEqualTo(user.getId());
            assertThat(auth.getRole()).isEqualTo("USER");
            assertThat(auth.getDeleted()).isFalse();
        });
        assertThat(oauthAccountRepository.findAuthView(OAuthAccount.Provider.facebook, account.getProviderUserId())).isEmpty();
        assertThat(oauthAccountRepository.findByUserId(user.getId())).hasSize(1);
    }

    @Test
    void findsWardWithProvinceAndRejectsProvinceId() {
        Statistics statistics = resetStatistics();

        Optional<WardView> ward = administrativeUnitRepository.findWardWithProvince(BEN_NGHE_WARD);

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
        assertThat(ward).hasValueSatisfying(view -> {
            assertThat(view.getWardName()).isEqualTo("Phường Bến Nghé");
            assertThat(view.getProvinceId()).isEqualTo(HCM_PROVINCE);
            assertThat(view.getProvinceName()).isEqualTo("TP. Hồ Chí Minh");
        });
        assertThat(administrativeUnitRepository.findWardWithProvince(HCM_PROVINCE)).isEmpty();
    }

    static User newUser(String displayName, UUID wardId) {
        User user = User.builder()
                .displayName(displayName)
                .wardId(wardId)
                .build();
        return user;
    }
}
