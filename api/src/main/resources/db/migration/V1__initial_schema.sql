-- Migration V1: Initial schema for ArenaFlow-be
-- Created: 2026-11-01
-- Description: Create all tables for User, Team, Match, Rating, Venue, Mercenary, Chat, Notification

-- ================================================================
-- 1. User & Authentication
-- ================================================================

-- 1.1 Administrative units (Vietnam provinces/districts/wards)
CREATE TABLE administrative_unit (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    level VARCHAR(20) NOT NULL CHECK (level IN ('province', 'district', 'ward')),
    parent_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    FOREIGN KEY (parent_id) REFERENCES administrative_unit(id) ON DELETE RESTRICT
);

CREATE INDEX idx_administrative_unit_parent ON administrative_unit(parent_id);
CREATE INDEX idx_administrative_unit_level ON administrative_unit(level);

-- 1.2 User table
CREATE TABLE "user" (
    id UUID PRIMARY KEY,
    display_name VARCHAR(100) NOT NULL,
    avatar_url VARCHAR(500),
    bio TEXT,
    preferred_position VARCHAR(50),
    date_of_birth DATE,
    gender VARCHAR(20),
    district_id UUID,
    phone_number VARCHAR(20),
    reliability_score INTEGER NOT NULL DEFAULT 80 CHECK (reliability_score >= 0 AND reliability_score <= 100),
    total_matches_completed INTEGER NOT NULL DEFAULT 0,
    total_matches_confirmed INTEGER NOT NULL DEFAULT 0 CHECK (total_matches_completed <= total_matches_confirmed),
    average_rating DECIMAL(3,2) CHECK (average_rating IS NULL OR (average_rating >= 1.0 AND average_rating <= 5.0)),
    report_count_noshow INTEGER NOT NULL DEFAULT 0 CHECK (report_count_noshow >= 0),
    report_count_bad_play INTEGER NOT NULL DEFAULT 0 CHECK (report_count_bad_play >= 0),
    report_count_bad_communication INTEGER NOT NULL DEFAULT 0 CHECK (report_count_bad_communication >= 0),
    report_count_skill_mismatch INTEGER NOT NULL DEFAULT 0 CHECK (report_count_skill_mismatch >= 0),
    clean_streak INTEGER NOT NULL DEFAULT 0 CHECK (clean_streak >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    FOREIGN KEY (district_id) REFERENCES administrative_unit(id) ON DELETE RESTRICT
);

CREATE UNIQUE INDEX idx_user_phone_unique ON "user"(phone_number) WHERE phone_number IS NOT NULL AND deleted_at IS NULL;
CREATE INDEX idx_user_district ON "user"(district_id);
CREATE INDEX idx_user_deleted ON "user"(deleted_at);

-- 1.3 OAuth accounts
CREATE TABLE oauth_account (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    provider VARCHAR(50) NOT NULL CHECK (provider IN ('google', 'facebook', 'phone')),
    provider_user_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    FOREIGN KEY (user_id) REFERENCES "user"(id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX idx_oauth_account_provider ON oauth_account(provider, provider_user_id);
CREATE INDEX idx_oauth_account_user ON oauth_account(user_id);

-- ================================================================
-- 2. Team
-- ================================================================

-- 2.1 Team table
CREATE TABLE team (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    created_by UUID NOT NULL,
    avatar_url VARCHAR(500),
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    reliability_score INTEGER NOT NULL DEFAULT 80 CHECK (reliability_score >= 0 AND reliability_score <= 100),
    total_matches_completed INTEGER NOT NULL DEFAULT 0,
    total_matches_confirmed INTEGER NOT NULL DEFAULT 0,
    average_rating DECIMAL(3,2) CHECK (average_rating IS NULL OR (average_rating >= 1.0 AND average_rating <= 5.0)),
    report_count_noshow INTEGER NOT NULL DEFAULT 0 CHECK (report_count_noshow >= 0),
    report_count_bad_play INTEGER NOT NULL DEFAULT 0 CHECK (report_count_bad_play >= 0),
    report_count_bad_communication INTEGER NOT NULL DEFAULT 0 CHECK (report_count_bad_communication >= 0),
    report_count_skill_mismatch INTEGER NOT NULL DEFAULT 0 CHECK (report_count_skill_mismatch >= 0),
    clean_streak INTEGER NOT NULL DEFAULT 0 CHECK (clean_streak >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    FOREIGN KEY (created_by) REFERENCES "user"(id) ON DELETE RESTRICT
);

CREATE INDEX idx_team_created_by ON team(created_by);
CREATE INDEX idx_team_status ON team(status);

-- 2.2 Team members (many-to-many with role)
CREATE TABLE team_member (
    id UUID PRIMARY KEY,
    team_id UUID NOT NULL,
    user_id UUID NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('CAPTAIN', 'MEMBER')),
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'ACTIVE', 'LEFT')),
    invited_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    joined_at TIMESTAMPTZ,
    left_at TIMESTAMPTZ,
    FOREIGN KEY (team_id) REFERENCES team(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES "user"(id) ON DELETE CASCADE,
    CHECK (joined_at IS NULL OR joined_at >= invited_at),
    CHECK (left_at IS NULL OR left_at >= COALESCE(joined_at, invited_at))
);

CREATE UNIQUE INDEX idx_team_member_active ON team_member(team_id, user_id, status) WHERE status = 'ACTIVE';
CREATE INDEX idx_team_member_team_role ON team_member(team_id, role, status);
CREATE INDEX idx_team_member_user ON team_member(user_id, status);

-- 2.3 Team tags
CREATE TABLE team_tag (
    id UUID PRIMARY KEY,
    team_id UUID NOT NULL,
    tag VARCHAR(50) NOT NULL CHECK (tag IN ('Chill', 'Nhiệt tình', 'Máu lửa', 'Vui chơi', 'Rèn luyện', 'Thi đấu', 'Mới bắt đầu', 'Đã có kinh nghiệm', 'Lão làng')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    FOREIGN KEY (team_id) REFERENCES team(id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX idx_team_tag_unique ON team_tag(team_id, tag);
CREATE INDEX idx_team_tag_team ON team_tag(team_id);

-- 2.4 Team captain history
CREATE TABLE team_captain_history (
    id UUID PRIMARY KEY,
    team_id UUID NOT NULL,
    from_user_id UUID,
    to_user_id UUID NOT NULL,
    transferred_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    FOREIGN KEY (team_id) REFERENCES team(id) ON DELETE CASCADE,
    FOREIGN KEY (from_user_id) REFERENCES "user"(id) ON DELETE SET NULL,
    FOREIGN KEY (to_user_id) REFERENCES "user"(id) ON DELETE RESTRICT
);

CREATE INDEX idx_team_captain_history ON team_captain_history(team_id, transferred_at DESC);

-- ================================================================
-- 3. Sport
-- ================================================================

CREATE TABLE sport (
    id UUID PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Insert initial sport
INSERT INTO sport (name, is_active) VALUES ('Bóng đá', TRUE);

-- ================================================================
-- 4. Venue
-- ================================================================

-- 4.1 Venue table
CREATE TABLE venue (
    id UUID PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    address TEXT NOT NULL,
    latitude DECIMAL(10,7) NOT NULL,
    longitude DECIMAL(10,7) NOT NULL,
    created_by_type VARCHAR(20) NOT NULL CHECK (created_by_type IN ('ADMIN', 'USER')),
    created_by_user_id UUID,
    phone VARCHAR(20),
    website VARCHAR(500),
    venue_type VARCHAR(50),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    last_selected_at TIMESTAMPTZ,
    admin_verified_inactive_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    FOREIGN KEY (created_by_user_id) REFERENCES "user"(id) ON DELETE SET NULL,
    CHECK ((created_by_type = 'USER' AND created_by_user_id IS NOT NULL) OR (created_by_type = 'ADMIN'))
);

CREATE INDEX idx_venue_location ON venue(latitude, longitude, status) WHERE status = 'ACTIVE';
CREATE INDEX idx_venue_last_selected ON venue(status, last_selected_at);
CREATE INDEX idx_venue_created_by ON venue(created_by_user_id);

-- 4.2 Venue images
CREATE TABLE venue_image (
    id UUID PRIMARY KEY,
    venue_id UUID NOT NULL,
    image_url VARCHAR(500) NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    display_order INTEGER NOT NULL CHECK (display_order > 0),
    uploaded_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    FOREIGN KEY (venue_id) REFERENCES venue(id) ON DELETE CASCADE,
    FOREIGN KEY (uploaded_by) REFERENCES "user"(id) ON DELETE SET NULL
);

CREATE UNIQUE INDEX idx_venue_image_primary ON venue_image(venue_id, is_primary) WHERE is_primary = TRUE;
CREATE INDEX idx_venue_image_order ON venue_image(venue_id, display_order);

-- 4.3 Venue reports
CREATE TABLE venue_report (
    id UUID PRIMARY KEY,
    venue_id UUID NOT NULL,
    reporter_id UUID NOT NULL,
    reason TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    FOREIGN KEY (venue_id) REFERENCES venue(id) ON DELETE CASCADE,
    FOREIGN KEY (reporter_id) REFERENCES "user"(id) ON DELETE SET NULL
);

CREATE INDEX idx_venue_report_venue ON venue_report(venue_id, created_at DESC);
CREATE INDEX idx_venue_report_reporter ON venue_report(reporter_id);

-- ================================================================
-- 5. Match & Pairing
-- ================================================================

-- 5.1 Match table
CREATE TABLE match (
    id UUID PRIMARY KEY,
    type VARCHAR(20) NOT NULL CHECK (type IN ('HAS_VENUE', 'NEED_VENUE')),
    author_team_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'CONFIRMED', 'ARCHIVED', 'COMPLETED', 'EXPIRED', 'CANCELLED')),
    sport_id UUID NOT NULL,
    format VARCHAR(10),
    age_note TEXT,
    match_nature TEXT,
    venue_id UUID,
    match_start_time TIMESTAMPTZ NOT NULL,
    match_end_time TIMESTAMPTZ NOT NULL CHECK (match_end_time > match_start_time),
    expires_at TIMESTAMPTZ NOT NULL CHECK (expires_at <= match_start_time),
    search_lat DECIMAL(10,7),
    search_lng DECIMAL(10,7),
    search_radius_km DECIMAL(5,2),
    cost_type VARCHAR(50),
    cost_split VARCHAR(50),
    cost_venue_amount BIGINT,
    cost_drinks_amount BIGINT,
    cost_deposit_holder VARCHAR(100),
    confirmed_opponent_team_id UUID,
    confirmed_venue_id UUID,
    confirmed_match_time TIMESTAMPTZ,
    cancelled_by_team_id UUID,
    cancelled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    FOREIGN KEY (author_team_id) REFERENCES team(id) ON DELETE RESTRICT,
    FOREIGN KEY (sport_id) REFERENCES sport(id) ON DELETE RESTRICT,
    FOREIGN KEY (venue_id) REFERENCES venue(id) ON DELETE RESTRICT,
    FOREIGN KEY (confirmed_opponent_team_id) REFERENCES team(id) ON DELETE RESTRICT,
    FOREIGN KEY (confirmed_venue_id) REFERENCES venue(id) ON DELETE RESTRICT,
    FOREIGN KEY (cancelled_by_team_id) REFERENCES team(id) ON DELETE RESTRICT,
    CHECK ((type = 'HAS_VENUE' AND venue_id IS NOT NULL) OR (type = 'NEED_VENUE' AND venue_id IS NULL)),
    CHECK ((status = 'CONFIRMED' AND confirmed_opponent_team_id IS NOT NULL) OR (status != 'CONFIRMED')),
    CHECK ((status = 'CANCELLED' AND cancelled_by_team_id IS NOT NULL AND cancelled_at IS NOT NULL) OR (status != 'CANCELLED'))
);

CREATE INDEX idx_match_author_status ON match(author_team_id, status);
CREATE INDEX idx_match_expiry ON match(status, expires_at);
CREATE INDEX idx_match_archived ON match(status, match_end_time);
CREATE INDEX idx_match_sport ON match(sport_id, status, match_start_time);
CREATE INDEX idx_match_search_gps ON match(search_lat, search_lng) WHERE type = 'NEED_VENUE' AND status = 'OPEN';

-- 5.2 Match venue options (for NEED_VENUE type)
CREATE TABLE match_venue_option (
    id UUID PRIMARY KEY,
    match_id UUID NOT NULL,
    venue_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    FOREIGN KEY (match_id) REFERENCES match(id) ON DELETE CASCADE,
    FOREIGN KEY (venue_id) REFERENCES venue(id) ON DELETE RESTRICT
);

CREATE INDEX idx_match_venue_option_match ON match_venue_option(match_id);

-- 5.3 Match time options (for NEED_VENUE type)
CREATE TABLE match_time_option (
    id UUID PRIMARY KEY,
    match_id UUID NOT NULL,
    proposed_start_time TIMESTAMPTZ NOT NULL,
    proposed_end_time TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    FOREIGN KEY (match_id) REFERENCES match(id) ON DELETE CASCADE
);

CREATE INDEX idx_match_time_option_match ON match_time_option(match_id);

-- 5.4 Cost custom items
CREATE TABLE cost_custom_item (
    id UUID PRIMARY KEY,
    match_id UUID NOT NULL,
    item_name VARCHAR(100) NOT NULL,
    amount BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    FOREIGN KEY (match_id) REFERENCES match(id) ON DELETE CASCADE
);

CREATE INDEX idx_cost_custom_item_match ON cost_custom_item(match_id);

-- 5.5 Match requests (for HAS_VENUE type)
CREATE TABLE match_request (
    id UUID PRIMARY KEY,
    match_id UUID NOT NULL,
    requester_team_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'WITHDRAWN', 'SUPERSEDED', 'EXPIRED')),
    requested_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    responded_at TIMESTAMPTZ,
    withdrawn_at TIMESTAMPTZ,
    FOREIGN KEY (match_id) REFERENCES match(id) ON DELETE CASCADE,
    FOREIGN KEY (requester_team_id) REFERENCES team(id) ON DELETE RESTRICT,
    CHECK ((status = 'REJECTED' AND responded_at IS NOT NULL) OR (status != 'REJECTED')),
    CHECK ((status = 'WITHDRAWN' AND withdrawn_at IS NOT NULL) OR (status != 'WITHDRAWN'))
);

CREATE UNIQUE INDEX idx_match_request_active ON match_request(match_id, requester_team_id) WHERE status NOT IN ('WITHDRAWN', 'EXPIRED');
CREATE INDEX idx_match_request_match ON match_request(match_id, status);
CREATE INDEX idx_match_request_requester ON match_request(requester_team_id, status);

-- 5.6 Pairing (for NEED_VENUE type)
CREATE TABLE pairing (
    id UUID PRIMARY KEY,
    match_id UUID NOT NULL,
    interested_team_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'NEGOTIATING' CHECK (status IN ('NEGOTIATING', 'CONFIRMED', 'EXPIRED', 'SUPERSEDED')),
    author_confirmed_at TIMESTAMPTZ,
    interested_confirmed_at TIMESTAMPTZ,
    final_confirmed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    FOREIGN KEY (match_id) REFERENCES match(id) ON DELETE CASCADE,
    FOREIGN KEY (interested_team_id) REFERENCES team(id) ON DELETE RESTRICT,
    CHECK ((status = 'CONFIRMED' AND author_confirmed_at IS NOT NULL AND interested_confirmed_at IS NOT NULL AND final_confirmed_at IS NOT NULL) OR (status != 'CONFIRMED'))
);

CREATE UNIQUE INDEX idx_pairing_active ON pairing(match_id, interested_team_id) WHERE status NOT IN ('EXPIRED', 'SUPERSEDED');
CREATE INDEX idx_pairing_match ON pairing(match_id, status);
CREATE INDEX idx_pairing_team ON pairing(interested_team_id, status);
CREATE INDEX idx_pairing_race ON pairing(match_id, final_confirmed_at) WHERE status = 'CONFIRMED';

-- ================================================================
-- 6. Rating & Report
-- ================================================================

-- 6.1 Rating table
CREATE TABLE rating (
    id UUID PRIMARY KEY,
    match_id UUID NOT NULL,
    rater_type VARCHAR(20) NOT NULL CHECK (rater_type IN ('TEAM', 'INDIVIDUAL')),
    rater_id UUID NOT NULL,
    ratee_type VARCHAR(20) NOT NULL CHECK (ratee_type IN ('TEAM', 'INDIVIDUAL')),
    ratee_id UUID NOT NULL,
    score DECIMAL(2,1) NOT NULL CHECK (score >= 1.0 AND score <= 5.0),
    comment TEXT,
    match_score_home INTEGER CHECK (match_score_home IS NULL OR match_score_home >= 0),
    match_score_away INTEGER CHECK (match_score_away IS NULL OR match_score_away >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_rating_unique ON rating(match_id, rater_type, rater_id, ratee_type, ratee_id);
CREATE INDEX idx_rating_ratee ON rating(ratee_type, ratee_id);

-- 6.2 Report table
CREATE TABLE report (
    id UUID PRIMARY KEY,
    match_id UUID NOT NULL,
    reporter_type VARCHAR(20) NOT NULL CHECK (reporter_type IN ('TEAM', 'INDIVIDUAL')),
    reporter_id UUID NOT NULL,
    reported_type VARCHAR(20) NOT NULL CHECK (reported_type IN ('TEAM', 'INDIVIDUAL')),
    reported_id UUID NOT NULL,
    report_type VARCHAR(50) NOT NULL CHECK (report_type IN ('NOSHOW', 'BAD_PLAY', 'BAD_COMMUNICATION', 'SKILL_MISMATCH')),
    reason TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'CONFIRMED', 'REJECTED')),
    admin_reviewed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_report_match ON report(match_id);
CREATE INDEX idx_report_reported ON report(reported_type, reported_id, report_type);
CREATE INDEX idx_report_status ON report(status, created_at);

-- ================================================================
-- 7. Mercenary
-- ================================================================

-- 7.1 Mercenary recruitment
CREATE TABLE mercenary_recruitment (
    id UUID PRIMARY KEY,
    match_id UUID NOT NULL,
    team_id UUID NOT NULL,
    position VARCHAR(50) NOT NULL CHECK (position IN ('Thủ môn', 'Hậu vệ', 'Tiền vệ', 'Tiền đạo')),
    quantity_needed INTEGER NOT NULL CHECK (quantity_needed > 0),
    quantity_approved INTEGER NOT NULL DEFAULT 0 CHECK (quantity_approved >= 0),
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'FILLED', 'EXPIRED', 'CLOSED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    FOREIGN KEY (match_id) REFERENCES match(id) ON DELETE CASCADE,
    FOREIGN KEY (team_id) REFERENCES team(id) ON DELETE RESTRICT,
    CHECK (quantity_approved <= quantity_needed)
);

CREATE INDEX idx_mercenary_recruitment_match ON mercenary_recruitment(match_id);
CREATE INDEX idx_mercenary_recruitment_team ON mercenary_recruitment(team_id, status);
CREATE INDEX idx_mercenary_recruitment_search ON mercenary_recruitment(status, position);

-- 7.2 Mercenary applications
CREATE TABLE mercenary_application (
    id UUID PRIMARY KEY,
    recruitment_id UUID NOT NULL,
    applicant_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'WITHDRAWN')),
    applied_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    responded_at TIMESTAMPTZ,
    withdrawn_at TIMESTAMPTZ,
    FOREIGN KEY (recruitment_id) REFERENCES mercenary_recruitment(id) ON DELETE CASCADE,
    FOREIGN KEY (applicant_id) REFERENCES "user"(id) ON DELETE RESTRICT,
    CHECK ((status IN ('APPROVED', 'REJECTED') AND responded_at IS NOT NULL) OR (status NOT IN ('APPROVED', 'REJECTED'))),
    CHECK ((status = 'WITHDRAWN' AND withdrawn_at IS NOT NULL) OR (status != 'WITHDRAWN'))
);

CREATE UNIQUE INDEX idx_mercenary_application_active ON mercenary_application(recruitment_id, applicant_id) WHERE status NOT IN ('WITHDRAWN', 'REJECTED');
CREATE INDEX idx_mercenary_application_recruitment ON mercenary_application(recruitment_id, status);
CREATE INDEX idx_mercenary_application_applicant ON mercenary_application(applicant_id, status);

-- ================================================================
-- 8. Chat & Notification
-- ================================================================

-- 8.1 Chat messages
CREATE TABLE chat_message (
    id UUID PRIMARY KEY,
    pairing_id UUID NOT NULL,
    sender_user_id UUID NOT NULL,
    message TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    FOREIGN KEY (pairing_id) REFERENCES pairing(id) ON DELETE CASCADE,
    FOREIGN KEY (sender_user_id) REFERENCES "user"(id) ON DELETE SET NULL
);

CREATE INDEX idx_chat_message_pairing ON chat_message(pairing_id, created_at);
CREATE INDEX idx_chat_message_retention ON chat_message(created_at);

-- 8.2 Notifications
CREATE TABLE notification (
    id UUID PRIMARY KEY,
    recipient_user_id UUID NOT NULL,
    type VARCHAR(50) NOT NULL CHECK (type IN ('MATCH_REQUEST_RECEIVED', 'REQUEST_ACCEPTED', 'REQUEST_REJECTED', 'MATCH_CONFIRMED', 'MATCH_CANCELLED', 'PAIRING_NEW', 'REMINDER_BEFORE_MATCH', 'MATCH_COMPLETED', 'RATING_RECEIVED', 'REPORT_RECEIVED', 'MERCENARY_APPLICATION', 'TEAM_INVITATION')),
    title VARCHAR(200) NOT NULL,
    body TEXT NOT NULL,
    reference_type VARCHAR(50),
    reference_id UUID,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    FOREIGN KEY (recipient_user_id) REFERENCES "user"(id) ON DELETE CASCADE
);

CREATE INDEX idx_notification_recipient ON notification(recipient_user_id, is_read, created_at DESC);
CREATE INDEX idx_notification_retention ON notification(created_at);

-- ================================================================
-- Migration complete
-- ================================================================
