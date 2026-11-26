-- rating: sao nguyên 1..5, "đội kia không đến", tỷ số theo góc nhìn đội đánh giá, người gửi, FK trận
ALTER TABLE rating DROP CONSTRAINT rating_score_check;
ALTER TABLE rating ALTER COLUMN score DROP NOT NULL;
ALTER TABLE rating ALTER COLUMN score TYPE SMALLINT USING score::smallint;
ALTER TABLE rating ADD CONSTRAINT rating_score_check CHECK (score IS NULL OR score BETWEEN 1 AND 5);
ALTER TABLE rating ADD COLUMN is_no_show BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE rating RENAME COLUMN match_score_home TO rater_team_score;
ALTER TABLE rating RENAME COLUMN match_score_away TO ratee_team_score;
ALTER TABLE rating RENAME CONSTRAINT rating_match_score_home_check TO rating_rater_team_score_check;
ALTER TABLE rating RENAME CONSTRAINT rating_match_score_away_check TO rating_ratee_team_score_check;
ALTER TABLE rating ADD COLUMN created_by_user_id UUID NOT NULL REFERENCES "user"(id) ON DELETE RESTRICT;
ALTER TABLE rating ADD CONSTRAINT rating_match_fk FOREIGN KEY (match_id) REFERENCES match(id) ON DELETE RESTRICT;
ALTER TABLE rating ADD CONSTRAINT rating_no_show_check CHECK (
    (is_no_show AND score IS NULL AND rater_team_score IS NULL AND ratee_team_score IS NULL)
    OR (NOT is_no_show AND score IS NOT NULL));
ALTER TABLE rating ADD CONSTRAINT rating_team_scores_pair_check CHECK ((rater_team_score IS NULL) = (ratee_team_score IS NULL));
ALTER TABLE rating ADD CONSTRAINT rating_comment_length_check CHECK (comment IS NULL OR char_length(comment) <= 500);
ALTER TABLE rating ADD CONSTRAINT rating_not_self_check CHECK (NOT (rater_type = ratee_type AND rater_id = ratee_id));

-- report: trạng thái chấp nhận ngay / tranh chấp / quyết định, điểm thực trừ, người gửi, FK trận, chống trùng
ALTER TABLE report DROP CONSTRAINT report_status_check;
UPDATE report SET status = 'AUTO_ACCEPTED' WHERE status = 'PENDING';
ALTER TABLE report ALTER COLUMN status SET DEFAULT 'AUTO_ACCEPTED';
ALTER TABLE report ADD CONSTRAINT report_status_check CHECK (status IN ('AUTO_ACCEPTED', 'DISPUTED', 'CONFIRMED', 'REJECTED'));
ALTER TABLE report DROP COLUMN admin_reviewed_at;
ALTER TABLE report ALTER COLUMN reason SET NOT NULL;
ALTER TABLE report ADD CONSTRAINT report_reason_length_check CHECK (char_length(reason) BETWEEN 1 AND 1000);
ALTER TABLE report ADD COLUMN reliability_penalty INTEGER NOT NULL DEFAULT 0 CHECK (reliability_penalty >= 0);
ALTER TABLE report ADD COLUMN created_by_user_id UUID NOT NULL REFERENCES "user"(id) ON DELETE RESTRICT;
ALTER TABLE report ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT (now() AT TIME ZONE 'Asia/Ho_Chi_Minh');
ALTER TABLE report ADD CONSTRAINT report_match_fk FOREIGN KEY (match_id) REFERENCES match(id) ON DELETE RESTRICT;
ALTER TABLE report ADD CONSTRAINT report_not_self_check CHECK (NOT (reporter_type = reported_type AND reporter_id = reported_id));
DROP INDEX idx_report_match;
DROP INDEX idx_report_reported;
DROP INDEX idx_report_status;
CREATE UNIQUE INDEX idx_report_unique ON report(match_id, reporter_type, reporter_id, reported_type, reported_id, report_type);
CREATE INDEX idx_report_reported_created ON report(reported_type, reported_id, created_at DESC, id DESC);
CREATE INDEX idx_report_reporter_created ON report(reporter_type, reporter_id, created_at DESC, id DESC);

-- dispute: 1:1 với report bùng
CREATE TABLE dispute (
    id UUID PRIMARY KEY,
    report_id UUID NOT NULL REFERENCES report(id) ON DELETE RESTRICT,
    evidence TEXT NOT NULL CHECK (char_length(evidence) BETWEEN 1 AND 1000),
    created_by_user_id UUID NOT NULL REFERENCES "user"(id) ON DELETE RESTRICT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'RESOLVED')),
    decision VARCHAR(20) CHECK (decision IN ('CONFIRMED', 'REJECTED')),
    resolution_note VARCHAR(500) CHECK (resolution_note IS NULL OR char_length(resolution_note) BETWEEN 1 AND 500),
    resolved_by_user_id UUID REFERENCES "user"(id) ON DELETE RESTRICT,
    resolved_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT (now() AT TIME ZONE 'Asia/Ho_Chi_Minh'),
    updated_at TIMESTAMP NOT NULL DEFAULT (now() AT TIME ZONE 'Asia/Ho_Chi_Minh'),
    CONSTRAINT dispute_resolution_check CHECK (
        (status = 'PENDING' AND decision IS NULL AND resolution_note IS NULL AND resolved_by_user_id IS NULL AND resolved_at IS NULL)
        OR (status = 'RESOLVED' AND decision IS NOT NULL AND resolution_note IS NOT NULL AND resolved_by_user_id IS NOT NULL AND resolved_at IS NOT NULL))
);
CREATE UNIQUE INDEX idx_dispute_report ON dispute(report_id);
CREATE INDEX idx_dispute_status_created ON dispute(status, created_at, id);

-- team / user: tỉ lệ hoàn thành và sao trung bình tính khi đọc; huy hiệu xác minh là dữ liệu dẫn xuất
ALTER TABLE team DROP COLUMN total_matches_completed, DROP COLUMN total_matches_confirmed, DROP COLUMN average_rating;
ALTER TABLE "user" DROP COLUMN total_matches_completed, DROP COLUMN total_matches_confirmed, DROP COLUMN average_rating;
ALTER TABLE team ADD COLUMN verified_at TIMESTAMP;
