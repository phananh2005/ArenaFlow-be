-- V10: tuyển mercenary (tin tuyển, đơn ứng tuyển), trận ngoài hệ thống, chat theo đơn, đánh giá / report đội ↔ mercenary,
-- huy hiệu cá nhân. Chạy sau V9 (rating/report/dispute) và V7 (conversation). Mọi thời điểm là giờ Việt Nam (TIMESTAMP).

-- external_match: trận ngoài hệ thống do đội trưởng khai báo
CREATE TABLE external_match (
    id UUID PRIMARY KEY,
    team_id UUID NOT NULL REFERENCES team(id) ON DELETE RESTRICT,
    venue_id UUID NOT NULL REFERENCES venue(id) ON DELETE RESTRICT,
    match_start_time TIMESTAMP NOT NULL,
    match_end_time TIMESTAMP NOT NULL,
    skill_levels TEXT NOT NULL DEFAULT '[]',
    opponent_name VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED' CHECK (status IN ('SCHEDULED', 'CANCELLED')),
    cancelled_at TIMESTAMP,
    created_by_user_id UUID NOT NULL REFERENCES "user"(id) ON DELETE RESTRICT,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT (now() AT TIME ZONE 'Asia/Ho_Chi_Minh'),
    updated_at TIMESTAMP NOT NULL DEFAULT (now() AT TIME ZONE 'Asia/Ho_Chi_Minh'),
    CONSTRAINT external_match_time_check CHECK (match_end_time > match_start_time),
    CONSTRAINT external_match_cancel_check CHECK ((status = 'CANCELLED') = (cancelled_at IS NOT NULL))
);
CREATE INDEX idx_external_match_team ON external_match(team_id, match_start_time, id);
-- job báo huỷ trận: trận ngoài bị huỷ gần đây
CREATE INDEX idx_external_match_cancelled ON external_match(match_end_time) WHERE status = 'CANCELLED';

-- mercenary_recruitment: tin của kèo HOẶC trận ngoài; người tạo, thời điểm đóng; một tin OPEN/FILLED mỗi (trận, đội, vị trí)
ALTER TABLE mercenary_recruitment ALTER COLUMN match_id DROP NOT NULL;
ALTER TABLE mercenary_recruitment ADD COLUMN external_match_id UUID REFERENCES external_match(id) ON DELETE RESTRICT;
ALTER TABLE mercenary_recruitment ADD CONSTRAINT mercenary_recruitment_game_check
    CHECK ((match_id IS NULL) <> (external_match_id IS NULL));
ALTER TABLE mercenary_recruitment ADD COLUMN created_by_user_id UUID NOT NULL REFERENCES "user"(id) ON DELETE RESTRICT;
ALTER TABLE mercenary_recruitment ADD COLUMN closed_at TIMESTAMP;
-- mercenary_application: đã báo mercenary APPROVED về việc trận bị huỷ (chống báo trùng giữa huỷ trận ngoài và job)
ALTER TABLE mercenary_application ADD COLUMN game_cancel_notified_at TIMESTAMP;
CREATE INDEX idx_mercenary_application_cancel_notice ON mercenary_application(recruitment_id)
    WHERE status = 'APPROVED' AND game_cancel_notified_at IS NULL;
ALTER TABLE mercenary_recruitment ADD CONSTRAINT mercenary_recruitment_closed_check
    CHECK ((status = 'CLOSED') = (closed_at IS NOT NULL));
DROP INDEX idx_mercenary_recruitment_search;
DROP INDEX idx_mercenary_recruitment_match;
CREATE UNIQUE INDEX idx_mercenary_recruitment_match_position ON mercenary_recruitment(match_id, team_id, position)
    WHERE status IN ('OPEN', 'FILLED') AND match_id IS NOT NULL;
CREATE UNIQUE INDEX idx_mercenary_recruitment_external_position ON mercenary_recruitment(external_match_id, position)
    WHERE status IN ('OPEN', 'FILLED') AND external_match_id IS NOT NULL;
CREATE INDEX idx_mercenary_recruitment_match_all ON mercenary_recruitment(match_id) WHERE match_id IS NOT NULL;
CREATE INDEX idx_mercenary_recruitment_external_all ON mercenary_recruitment(external_match_id)
    WHERE external_match_id IS NOT NULL;

-- rating / report: gắn đơn ứng tuyển cho cặp đội ↔ mercenary; trận của kèo HOẶC trận ngoài; không có cặp cá nhân ↔ cá nhân
ALTER TABLE rating ALTER COLUMN match_id DROP NOT NULL;
ALTER TABLE rating ADD COLUMN external_match_id UUID REFERENCES external_match(id) ON DELETE RESTRICT;
ALTER TABLE rating ADD COLUMN application_id UUID REFERENCES mercenary_application(id) ON DELETE RESTRICT;
ALTER TABLE rating ADD CONSTRAINT rating_game_check CHECK ((match_id IS NULL) <> (external_match_id IS NULL));
ALTER TABLE rating ADD CONSTRAINT rating_application_check CHECK (
    ((rater_type = 'TEAM' AND ratee_type = 'TEAM') = (application_id IS NULL))
    AND NOT (rater_type = 'INDIVIDUAL' AND ratee_type = 'INDIVIDUAL')
    AND (external_match_id IS NULL OR application_id IS NOT NULL));
ALTER TABLE rating ADD CONSTRAINT rating_mercenary_fields_check CHECK (
    application_id IS NULL
    OR (rater_team_score IS NULL AND ratee_team_score IS NULL AND (rater_type = 'TEAM' OR NOT is_no_show)));
CREATE UNIQUE INDEX idx_rating_mercenary_unique ON rating(application_id, rater_type) WHERE application_id IS NOT NULL;
ALTER TABLE report ALTER COLUMN match_id DROP NOT NULL;
ALTER TABLE report ADD COLUMN external_match_id UUID REFERENCES external_match(id) ON DELETE RESTRICT;
ALTER TABLE report ADD COLUMN application_id UUID REFERENCES mercenary_application(id) ON DELETE RESTRICT;
ALTER TABLE report ADD CONSTRAINT report_game_check CHECK ((match_id IS NULL) <> (external_match_id IS NULL));
ALTER TABLE report ADD CONSTRAINT report_application_check CHECK (
    ((reporter_type = 'TEAM' AND reported_type = 'TEAM') = (application_id IS NULL))
    AND NOT (reporter_type = 'INDIVIDUAL' AND reported_type = 'INDIVIDUAL')
    AND (external_match_id IS NULL OR application_id IS NOT NULL));
CREATE UNIQUE INDEX idx_report_external_unique ON report(external_match_id, reporter_type, reporter_id, reported_type,
    reported_id, report_type) WHERE external_match_id IS NOT NULL;
CREATE INDEX idx_report_application ON report(application_id) WHERE application_id IS NOT NULL;

-- user: huy hiệu cá nhân (dữ liệu dẫn xuất, chỉ ghi bằng câu refresh có điều kiện)
ALTER TABLE "user" ADD COLUMN verified_at TIMESTAMP;

-- conversation (bảng + conversation_message tạo ở V7): thêm ngữ cảnh đơn ứng tuyển mercenary
ALTER TABLE conversation ADD COLUMN application_id UUID REFERENCES mercenary_application(id) ON DELETE RESTRICT;
ALTER TABLE conversation ADD COLUMN external_match_id UUID REFERENCES external_match(id) ON DELETE RESTRICT;
ALTER TABLE conversation DROP CONSTRAINT conversation_context_type_check;
ALTER TABLE conversation ADD CONSTRAINT conversation_context_type_check
    CHECK (context_type IN ('TEAM_MATCH', 'MERCENARY_APPLICATION'));
ALTER TABLE conversation ADD CONSTRAINT conversation_application_check
    CHECK ((context_type = 'MERCENARY_APPLICATION') = (application_id IS NOT NULL));
ALTER TABLE conversation ADD CONSTRAINT conversation_game_check CHECK ((match_id IS NULL) <> (external_match_id IS NULL));
ALTER TABLE conversation ADD CONSTRAINT conversation_mercenary_parties_check CHECK (
    context_type <> 'MERCENARY_APPLICATION' OR (party_a_type = 'TEAM' AND party_b_type = 'USER'));
CREATE UNIQUE INDEX idx_conversation_application ON conversation(application_id) WHERE application_id IS NOT NULL;
CREATE INDEX idx_conversation_match ON conversation(match_id) WHERE match_id IS NOT NULL;
CREATE INDEX idx_conversation_external_match ON conversation(external_match_id) WHERE external_match_id IS NOT NULL;
