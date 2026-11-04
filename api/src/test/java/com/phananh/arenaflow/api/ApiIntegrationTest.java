package com.phananh.arenaflow.api;

import com.phananh.arenaflow.domain.entity.team.Team;
import com.phananh.arenaflow.domain.entity.team.TeamMember;
import com.phananh.arenaflow.domain.entity.user.User;
import com.phananh.arenaflow.domain.repository.TeamMemberRepository;
import com.phananh.arenaflow.domain.repository.TeamRepository;
import com.phananh.arenaflow.domain.repository.UserRepository;
import com.phananh.arenaflow.application.support.security.JwtTokenService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.OffsetDateTime;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

/**
 * Integration test API đầu-cuối (security + validation + handler + service + DB dev qua .env.dev).
 * MockMvc chạy cùng thread → @Transactional rollback dữ liệu sau mỗi test.
 */
@SpringBootTest
@Transactional
public abstract class ApiIntegrationTest {

    @Autowired private WebApplicationContext context;
    @Autowired protected UserRepository userRepository;
    @Autowired protected TeamRepository teamRepository;
    @Autowired protected TeamMemberRepository teamMemberRepository;
    @Autowired protected JwtTokenService jwtTokenService;
    @Autowired protected EntityManager entityManager;

    protected MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    protected User saveUser(String displayName, User.Role role) {
        User user = new User();
        user.setDisplayName(displayName);
        user.setRole(role);
        return userRepository.save(user);
    }

    protected User saveUser(String displayName) {
        return saveUser(displayName, User.Role.USER);
    }

    protected Team saveTeam(String name, User captain) {
        Team team = new Team();
        team.setName(name);
        team.setCreatedBy(captain.getId());
        teamRepository.save(team);
        saveMember(team, captain, TeamMember.Role.CAPTAIN, TeamMember.Status.ACTIVE);
        return team;
    }

    protected TeamMember saveMember(Team team, User user, TeamMember.Role role, TeamMember.Status status) {
        TeamMember member = new TeamMember();
        member.setTeamId(team.getId());
        member.setUserId(user.getId());
        member.setRole(role);
        member.setStatus(status);
        OffsetDateTime now = OffsetDateTime.now();
        member.setInvitedAt(now);
        if (status == TeamMember.Status.ACTIVE) {
            member.setJoinedAt(now);
        }
        return teamMemberRepository.save(member);
    }

    protected void flush() {
        entityManager.flush();
        entityManager.clear();
    }

    protected String bearer(User user) {
        return "Bearer " + jwtTokenService.issueAccessToken(user.getId(), user.getRole());
    }

    /** Request JSON kèm access token của {@code user}. */
    protected MockHttpServletRequestBuilder as(User user, MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, bearer(user)).contentType(MediaType.APPLICATION_JSON);
    }

    protected static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }
}
