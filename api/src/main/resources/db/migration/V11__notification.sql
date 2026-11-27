-- V11: Thông báo in-app (NOTIFICATION-001)
-- Cần V6–V10: bảng external_match (V10), cột match_request.responded_at (V7), pairing.final_confirmed_at (V8).

-- notification: thêm loại thông báo (giữ 12 loại của V1), ràng buộc màn hình liên quan, index danh sách theo thời gian
ALTER TABLE notification DROP CONSTRAINT notification_type_check;
ALTER TABLE notification ADD CONSTRAINT notification_type_check CHECK (type IN (
    'WELCOME', 'TEAM_INVITATION', 'TEAM_INVITATION_ACCEPTED', 'TEAM_INVITATION_REJECTED',
    'CAPTAIN_TRANSFERRED', 'TEAM_MARKED_INACTIVE', 'TEAM_REACTIVATED',
    'MATCH_REQUEST_RECEIVED', 'REQUEST_WITHDRAWN', 'REQUEST_ACCEPTED', 'REQUEST_REJECTED', 'REQUEST_SUPERSEDED', 'REQUEST_EXPIRED',
    'PAIRING_NEW', 'PAIRING_SUPERSEDED', 'PAIRING_EXPIRED',
    'MATCH_CONFIRMED', 'MATCH_CANCELLED', 'MATCH_EXPIRED', 'REMINDER_BEFORE_MATCH', 'MATCH_COMPLETED', 'MERCENARY_RATING_REMINDER',
    'RATING_RECEIVED', 'REPORT_RECEIVED', 'REPORT_DISPUTED', 'DISPUTE_RESOLVED', 'DISPUTE_CREATED',
    'VERIFICATION_BADGE_GRANTED', 'VERIFICATION_BADGE_REVOKED',
    'MERCENARY_APPLICATION', 'MERCENARY_APPLICATION_APPROVED', 'MERCENARY_APPLICATION_REJECTED',
    'MERCENARY_WITHDRAWN', 'MERCENARY_GAME_CANCELLED',
    'VENUE_ADMIN_ALERT'));
ALTER TABLE notification ADD CONSTRAINT notification_reference_type_check CHECK (reference_type IS NULL OR reference_type IN (
    'MATCH', 'PAIRING', 'TEAM', 'TEAM_INVITATION', 'USER', 'VENUE', 'REPORT', 'DISPUTE', 'MERCENARY_DISPUTE', 'MERCENARY_APPLICATION'));
ALTER TABLE notification ADD CONSTRAINT notification_reference_check CHECK ((reference_type IS NULL) = (reference_id IS NULL));
DROP INDEX idx_notification_recipient;
CREATE INDEX idx_notification_recipient_time ON notification(recipient_user_id, created_at DESC, id DESC);
CREATE INDEX idx_notification_recipient_unread ON notification(recipient_user_id, created_at DESC, id DESC) WHERE NOT is_read;
-- idx_notification_retention (created_at) của V1 giữ nguyên — job xoá 30 ngày

-- match_reminder: dấu "đã nhắc" — một dòng mỗi (kèo / trận ngoài, loại nhắc)
CREATE TABLE match_reminder (
    id UUID PRIMARY KEY,
    kind VARCHAR(20) NOT NULL CHECK (kind IN ('BEFORE_START', 'AFTER_END')),
    match_id UUID REFERENCES match(id) ON DELETE RESTRICT,
    external_match_id UUID REFERENCES external_match(id) ON DELETE RESTRICT,
    sent_at TIMESTAMP NOT NULL,
    CONSTRAINT match_reminder_game_check CHECK ((match_id IS NULL) <> (external_match_id IS NULL))
);
CREATE UNIQUE INDEX idx_match_reminder_match ON match_reminder(match_id, kind) WHERE match_id IS NOT NULL;
CREATE UNIQUE INDEX idx_match_reminder_external ON match_reminder(external_match_id, kind) WHERE external_match_id IS NOT NULL;
CREATE INDEX idx_match_reminder_sent ON match_reminder(sent_at);

-- job nhắc trước trận: kèo đã ghép / trận ngoài theo giờ bắt đầu
CREATE INDEX idx_match_confirmed_start ON match(match_start_time) WHERE status = 'CONFIRMED';
CREATE INDEX idx_external_match_scheduled_start ON external_match(match_start_time) WHERE status = 'SCHEDULED';
CREATE INDEX idx_external_match_scheduled_end ON external_match(match_end_time) WHERE status = 'SCHEDULED';   -- nhắc mercenary đánh giá
-- thông báo cho Admin: danh sách Admin đang hoạt động
CREATE INDEX idx_user_active_admin ON "user"(id) WHERE role = 'ADMIN' AND deleted_at IS NULL;
