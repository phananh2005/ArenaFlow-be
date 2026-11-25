package com.phananh.arenaflow.api.realtime;

import com.phananh.arenaflow.application.dto.request.CreateTeamRequest;
import com.phananh.arenaflow.application.dto.request.SendConversationMessageRequest;
import com.phananh.arenaflow.application.dto.request.SendMatchRequestRequest;
import com.phananh.arenaflow.application.service.ConversationService;
import com.phananh.arenaflow.application.service.MatchRequestService;
import com.phananh.arenaflow.application.service.TeamService;
import com.phananh.arenaflow.application.support.security.JwtTokenService;
import com.phananh.arenaflow.common.time.AppTime;
import com.phananh.arenaflow.domain.entity.user.User;
import com.phananh.arenaflow.domain.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kênh STOMP thật trên cổng ngẫu nhiên (DB dev, dữ liệu commit thật và được xoá sau test): xác thực CONNECT, chặn đích
 * sai mẫu, frame MESSAGE_CREATED chỉ tới sau khi tin được lưu.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StompChatIntegrationTest {

    @LocalServerPort private int port;
    @Autowired private JwtTokenService jwtTokenService;
    @Autowired private UserRepository userRepository;
    @Autowired private TeamService teamService;
    @Autowired private MatchRequestService matchRequestService;
    @Autowired private ConversationService conversationService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private final List<UUID> users = new ArrayList<>();
    private final List<UUID> teams = new ArrayList<>();
    private UUID matchId;
    private UUID venueId;
    private User author;
    private User guest;
    private UUID conversationId;
    private WebSocketStompClient client;

    @BeforeEach
    void setUp() {
        author = saveUser("STOMP tác giả");
        guest = saveUser("STOMP khách");
        UUID authorTeam = team(author);
        UUID guestTeam = team(guest);
        venueId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO venue (id, name, address, latitude, longitude, created_by_type, created_by_user_id) "
                + "VALUES (?, 'Sân STOMP', 'Địa chỉ', 10.7, 106.7, 'USER', ?)", venueId, author.getId());
        UUID sportId = jdbcTemplate.queryForObject("SELECT id FROM sport WHERE is_active ORDER BY name LIMIT 1", UUID.class);
        matchId = UUID.randomUUID();
        LocalDateTime start = AppTime.now().plusDays(1);
        jdbcTemplate.update("INSERT INTO match (id, type, author_team_id, status, sport_id, format, venue_id, "
                + "match_start_time, match_end_time, expires_at) VALUES (?, 'HAS_VENUE', ?, 'OPEN', ?, 'FIVE_A_SIDE', ?, ?, ?, ?)",
                matchId, authorTeam, sportId, venueId, start, start.plusHours(2), start.minusHours(5));
        conversationId = matchRequestService.sendRequest(matchId, guest.getId(), new SendMatchRequestRequest(guestTeam))
                .conversationId();
        client = new WebSocketStompClient(new StandardWebSocketClient());
    }

    @AfterEach
    void tearDown() {
        client.stop();
        jdbcTemplate.update("DELETE FROM conversation WHERE match_id = ?", matchId);
        jdbcTemplate.update("DELETE FROM match_request WHERE match_id = ?", matchId);
        jdbcTemplate.update("DELETE FROM match WHERE id = ?", matchId);
        jdbcTemplate.update("DELETE FROM venue WHERE id = ?", venueId);
        teams.forEach(id -> jdbcTemplate.update("DELETE FROM team WHERE id = ?", id));
        users.forEach(id -> jdbcTemplate.update("DELETE FROM \"user\" WHERE id = ?", id));
    }

    private User saveUser(String name) {
        User user = userRepository.save(User.builder().displayName(name).role(User.Role.USER).build());
        users.add(user.getId());
        return user;
    }

    private UUID team(User captain) {
        UUID id = teamService.createTeam(captain.getId(), new CreateTeamRequest("STOMP " + UUID.randomUUID(), null, null,
                null)).teamId();
        teams.add(id);
        return id;
    }

    /** Kết nối kèm token; trả phiên, frame ERROR (header message) đưa vào {@code errors}. */
    private StompSession connect(String token, BlockingQueue<String> errors) throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + token);
        CompletableFuture<StompSession> future = client.connectAsync("ws://localhost:" + port + "/ws/websocket",
                new WebSocketHttpHeaders(), connectHeaders, new StompSessionHandlerAdapter() {
                    @Override
                    public Type getPayloadType(StompHeaders headers) {
                        return byte[].class;
                    }

                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        errors.add(String.valueOf(headers.getFirst("message")));
                    }

                    @Override
                    public void handleException(StompSession session, StompCommand command, StompHeaders headers,
                                                byte[] payload, Throwable exception) {
                        errors.add("EXCEPTION");
                    }
                });
        return future.get(10, TimeUnit.SECONDS);
    }

    @Test
    void connectWithRefreshTokenIsRejected() {
        BlockingQueue<String> errors = new LinkedBlockingQueue<>();
        String refresh = jwtTokenService.issueRefreshToken(guest.getId());

        boolean connected;
        try {
            connect(refresh, errors);
            connected = true;
        } catch (Exception exception) {
            connected = false;
        }

        assertThat(connected).isFalse();
    }

    @Test
    void wildcardSubscriptionGetsErrorFrame() throws Exception {
        BlockingQueue<String> errors = new LinkedBlockingQueue<>();
        StompSession session = connect(jwtTokenService.issueAccessToken(guest.getId(), User.Role.USER), errors);

        session.subscribe("/topic/**", new StringFrames(new LinkedBlockingQueue<>()));

        assertThat(errors.poll(5, TimeUnit.SECONDS)).isEqualTo("SYS_004");
    }

    @Test
    void participantReceivesMessageCreatedAfterSendCommits() throws Exception {
        BlockingQueue<String> errors = new LinkedBlockingQueue<>();
        BlockingQueue<String> frames = new LinkedBlockingQueue<>();
        StompSession session = connect(jwtTokenService.issueAccessToken(author.getId(), User.Role.USER), errors);
        StompHeaders subscribe = new StompHeaders();
        subscribe.setDestination("/topic/conversations/" + conversationId);
        subscribe.add("selector", "headers.foo == 'bar'");
        session.subscribe(subscribe, new StringFrames(frames));
        Thread.sleep(500);

        conversationService.send(conversationId, guest.getId(), new SendConversationMessageRequest("Xin chào"));

        String frame = frames.poll(5, TimeUnit.SECONDS);
        assertThat(frame).contains("\"type\":\"MESSAGE_CREATED\"").contains("Xin chào").doesNotContain("Z\"");
        assertThat(errors).isEmpty();
    }

    @Test
    void outsiderSubscriptionGetsChat003() throws Exception {
        User outsider = saveUser("STOMP người ngoài");
        BlockingQueue<String> errors = new LinkedBlockingQueue<>();
        StompSession session = connect(jwtTokenService.issueAccessToken(outsider.getId(), User.Role.USER), errors);

        session.subscribe("/topic/conversations/" + conversationId, new StringFrames(new LinkedBlockingQueue<>()));

        assertThat(errors.poll(5, TimeUnit.SECONDS)).isEqualTo("CHAT_003");
    }

    private record StringFrames(BlockingQueue<String> frames) implements StompFrameHandler {
        @Override
        public Type getPayloadType(StompHeaders headers) {
            return byte[].class;
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            frames.add(new String((byte[]) payload, StandardCharsets.UTF_8));
        }
    }
}
