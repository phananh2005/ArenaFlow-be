package com.phananh.ArenaFlow.service;

import com.phananh.ArenaFlow.dto.request.OAuthLoginRequest;
import com.phananh.ArenaFlow.entity.user.User;
import com.phananh.ArenaFlow.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Đăng ký-hoặc-đăng nhập trên DB dev (.env.dev); rollback sau mỗi test. */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
class AuthServiceIntegrationTest {

    @Autowired private AuthService authService;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private EntityManagerFactory entityManagerFactory;

    @Test
    void registersOnceThenLogsInWithDefaultNamePersistedWithinQueryLimit() {
        String email = "it-" + UUID.randomUUID() + "@example.com";
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        AuthResult first = authService.loginWithOAuth(new OAuthLoginRequest("GOOGLE", "token", email));

        // NFS W6: S tài khoản + I user + I oauth_account (+ U tên mặc định) ≤ 6.
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(6);
        assertThat(first.isNewUser()).isTrue();
        entityManager.flush();
        entityManager.clear();
        User saved = userRepository.findById(first.body().userId()).orElseThrow();
        String id = saved.getId().toString();
        assertThat(saved.getDisplayName()).isEqualTo("User " + id.substring(id.length() - 6));
        assertThat(saved.getRole()).isEqualTo(User.Role.USER);

        AuthResult second = authService.loginWithOAuth(new OAuthLoginRequest("GOOGLE", "token", email));

        assertThat(second.isNewUser()).isFalse();
        assertThat(second.body().userId()).isEqualTo(first.body().userId());
    }
}
