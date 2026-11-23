package com.phananh.arenaflow.domain.entity;

import com.phananh.arenaflow.domain.entity.chat.ChatMessage;
import com.phananh.arenaflow.domain.entity.chat.Notification;
import com.phananh.arenaflow.domain.entity.match.CostCustomItem;
import com.phananh.arenaflow.domain.entity.match.Match;
import com.phananh.arenaflow.domain.entity.match.MatchRequest;
import com.phananh.arenaflow.domain.entity.match.MatchTimeOption;
import com.phananh.arenaflow.domain.entity.match.MatchVenueOption;
import com.phananh.arenaflow.domain.entity.match.Pairing;
import com.phananh.arenaflow.domain.entity.match.Sport;
import com.phananh.arenaflow.domain.entity.mercenary.MercenaryApplication;
import com.phananh.arenaflow.domain.entity.mercenary.MercenaryRecruitment;
import com.phananh.arenaflow.domain.entity.rating.Rating;
import com.phananh.arenaflow.domain.entity.rating.Report;
import com.phananh.arenaflow.domain.entity.team.Team;
import com.phananh.arenaflow.domain.entity.team.TeamCaptainHistory;
import com.phananh.arenaflow.domain.entity.team.TeamMember;
import com.phananh.arenaflow.domain.entity.user.AdministrativeUnit;
import com.phananh.arenaflow.domain.entity.user.OAuthAccount;
import com.phananh.arenaflow.domain.entity.user.User;
import com.phananh.arenaflow.domain.entity.venue.Venue;
import com.phananh.arenaflow.domain.entity.venue.VenueImage;
import com.phananh.arenaflow.domain.entity.venue.VenueReport;
import com.phananh.arenaflow.domain.projection.AdminVenueReportView;
import com.phananh.arenaflow.domain.projection.CaptainHistoryView;
import com.phananh.arenaflow.domain.projection.MemberHistoryView;
import com.phananh.arenaflow.domain.projection.MemberView;
import com.phananh.arenaflow.domain.projection.ProfileView;
import com.phananh.arenaflow.domain.projection.TeamDetailView;
import com.phananh.arenaflow.domain.projection.TeamSummaryView;
import com.phananh.arenaflow.domain.projection.VenueDetailView;
import com.phananh.arenaflow.domain.projection.VenueSummaryView;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Mọi thời điểm của entity / projection là LocalDateTime. */
class EntityTimeTypesTest {

    private static final Set<Class<?>> FORBIDDEN =
            Set.of(OffsetDateTime.class, ZonedDateTime.class, Instant.class, Date.class, Timestamp.class);

    static Stream<Class<?>> entities() {
        return Stream.of(User.class, OAuthAccount.class, AdministrativeUnit.class, Team.class, TeamMember.class,
                TeamCaptainHistory.class, Sport.class, MatchVenueOption.class, CostCustomItem.class, Match.class,
                MatchTimeOption.class, MatchRequest.class, Pairing.class, Venue.class, VenueImage.class,
                VenueReport.class, Rating.class, Report.class, MercenaryRecruitment.class,
                MercenaryApplication.class, ChatMessage.class, Notification.class);
    }

    static Stream<Class<?>> projections() {
        return Stream.of(AdminVenueReportView.class, CaptainHistoryView.class, MemberHistoryView.class,
                MemberView.class, ProfileView.class, TeamDetailView.class, TeamSummaryView.class,
                VenueDetailView.class, VenueSummaryView.class);
    }

    @ParameterizedTest
    @MethodSource("entities")
    void entityTimeFieldsAreLocalDateTime(Class<?> entity) {
        List<Field> timeFields = timeFields(entity);

        assertThat(timeFields).isNotEmpty();
        assertThat(timeFields).allSatisfy(field -> assertThat(field.getType())
                .as(entity.getSimpleName() + "." + field.getName())
                .isNotIn(FORBIDDEN)
                .isEqualTo(LocalDateTime.class));
    }

    @ParameterizedTest
    @MethodSource("projections")
    void projectionTimeGettersReturnLocalDateTime(Class<?> projection) {
        List<Method> getters = Arrays.stream(projection.getMethods())
                .filter(method -> method.getName().endsWith("At"))
                .toList();

        assertThat(getters).isNotEmpty();
        assertThat(getters).allSatisfy(getter -> assertThat(getter.getReturnType())
                .as(projection.getSimpleName() + "." + getter.getName())
                .isEqualTo(LocalDateTime.class));
    }

    @ParameterizedTest
    @MethodSource("entities")
    void lifecycleCallbacksStampCurrentTimeTruncatedToMicros(Class<?> entity) throws Exception {
        Object instance = newInstance(entity);
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);

        for (Method callback : callbacks(entity)) {
            callback.setAccessible(true);
            callback.invoke(instance);
        }

        LocalDateTime after = LocalDateTime.now().plusSeconds(1);
        for (Field field : timeFields(entity)) {
            field.setAccessible(true);
            Object value = field.get(instance);
            if (value != null) {
                assertThat((LocalDateTime) value).as(entity.getSimpleName() + "." + field.getName())
                        .isBetween(before, after);
                assertThat(((LocalDateTime) value).getNano() % 1_000).isZero();
            }
        }
    }

    private static List<Field> timeFields(Class<?> entity) {
        return Arrays.stream(entity.getDeclaredFields())
                .filter(field -> field.getName().endsWith("At") || field.getName().endsWith("Time"))
                .toList();
    }

    private static List<Method> callbacks(Class<?> entity) {
        return Arrays.stream(entity.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(PrePersist.class)
                        || method.isAnnotationPresent(PreUpdate.class))
                .toList();
    }

    private static Object newInstance(Class<?> entity) throws Exception {
        Constructor<?> constructor = entity.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }
}
