package com.phananh.arenaflow.api.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigAuthenticationConverterTest {

    @Test
    void mapsRolesClaimToRoleAuthoritiesAndUsesSubjectAsName() {
        String userId = UUID.randomUUID().toString();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(userId)
                .claim("roles", List.of("ADMIN"))
                .claim("typ", "access")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

        AbstractAuthenticationToken authentication = new SecurityConfig().jwtAuthenticationConverter().convert(jwt);

        assertThat(authentication.getName()).isEqualTo(userId);
        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                // Spring Security 7 tự thêm authority FACTOR_* (cách xác thực); chỉ kiểm tra role.
                .filteredOn(authority -> authority.startsWith("ROLE_"))
                .containsExactly("ROLE_ADMIN");
    }
}
