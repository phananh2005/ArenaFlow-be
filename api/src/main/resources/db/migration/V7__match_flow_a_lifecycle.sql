-- MATCH-001 đợt 1: vòng đời kèo luồng A, chat dùng chung (conversation), chạy sau V6 (cột thời gian TIMESTAMP giờ VN).

-- match: khoá lạc quan, trình độ mong muốn, lý do huỷ, enum định dạng / chi phí
ALTER TABLE match ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE match ADD COLUMN skill_levels TEXT NOT NULL DEFAULT '[]';
ALTER TABLE match ADD COLUMN cancel_reason VARCHAR(500);
ALTER TABLE match ALTER COLUMN format TYPE VARCHAR(20);
ALTER TABLE match ADD CONSTRAINT match_format_check
    CHECK (format IS NULL OR format IN ('FIVE_A_SIDE', 'SEVEN_A_SIDE', 'ELEVEN_A_SIDE'));
ALTER TABLE match ADD CONSTRAINT match_has_venue_format_check CHECK (type <> 'HAS_VENUE' OR format IS NOT NULL);
ALTER TABLE match ADD CONSTRAINT match_cost_type_check
    CHECK (cost_type IS NULL OR cost_type IN ('VENUE_ONLY', 'DRINKS_ONLY', 'BOTH'));
ALTER TABLE match ADD CONSTRAINT match_cost_split_check
    CHECK (cost_split IS NULL OR cost_split IN ('FIFTY_FIFTY', 'SEVENTY_THIRTY_HOME', 'SEVENTY_THIRTY_LOSER', 'LOSER_PAYS_DRINKS'));
ALTER TABLE match ADD CONSTRAINT match_cost_amount_check
    CHECK ((cost_venue_amount IS NULL OR cost_venue_amount > 0) AND (cost_drinks_amount IS NULL OR cost_drinks_amount > 0));
CREATE INDEX idx_match_open_start ON match(match_start_time, id) WHERE status = 'OPEN';
CREATE INDEX idx_match_opponent_status ON match(confirmed_opponent_team_id, status);

-- match_request: lý do từ chối/rút; tối đa 1 ACCEPTED mỗi kèo
ALTER TABLE match_request ADD COLUMN reason VARCHAR(500);
CREATE UNIQUE INDEX idx_match_request_accepted ON match_request(match_id) WHERE status = 'ACCEPTED';

-- cost_custom_item: thứ tự client gửi (≤ 10 khoản), số tiền > 0
ALTER TABLE cost_custom_item ADD COLUMN display_order INTEGER;
UPDATE cost_custom_item c SET display_order = o.rn
    FROM (SELECT id, ROW_NUMBER() OVER (PARTITION BY match_id ORDER BY created_at, id) AS rn FROM cost_custom_item) o
    WHERE c.id = o.id;
ALTER TABLE cost_custom_item ALTER COLUMN display_order SET NOT NULL;
ALTER TABLE cost_custom_item ADD CONSTRAINT cost_custom_item_display_order_check CHECK (display_order > 0);
ALTER TABLE cost_custom_item ADD CONSTRAINT cost_custom_item_amount_check CHECK (amount > 0);
DROP INDEX idx_cost_custom_item_match;
CREATE UNIQUE INDEX idx_cost_custom_item_order ON cost_custom_item(match_id, display_order);

-- chat: bỏ chat_message theo pairing của V1 (chỉ xoá khi bảng rỗng); mọi chat dùng cuộc trò chuyện tổng quát
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM chat_message LIMIT 1) THEN
        RAISE EXCEPTION 'chat_message is not empty — aborting V7 migration';
    END IF;
END $$;
DROP TABLE chat_message;

-- conversation: loại ngữ cảnh + trận (tính hạn lưu) + hai bên (TEAM | USER). V7 chỉ có TEAM_MATCH
CREATE TABLE conversation (
    id UUID PRIMARY KEY,
    context_type VARCHAR(30) NOT NULL,
    match_id UUID REFERENCES match(id) ON DELETE RESTRICT,
    party_a_type VARCHAR(10) NOT NULL CHECK (party_a_type IN ('TEAM', 'USER')),
    party_a_id UUID NOT NULL,
    party_b_type VARCHAR(10) NOT NULL CHECK (party_b_type IN ('TEAM', 'USER')),
    party_b_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT (now() AT TIME ZONE 'Asia/Ho_Chi_Minh'),
    CONSTRAINT conversation_context_type_check CHECK (context_type IN ('TEAM_MATCH')),
    CONSTRAINT conversation_team_match_check CHECK (context_type <> 'TEAM_MATCH'
        OR (match_id IS NOT NULL AND party_a_type = 'TEAM' AND party_b_type = 'TEAM')),
    CONSTRAINT conversation_parties_check CHECK (NOT (party_a_type = party_b_type AND party_a_id = party_b_id))
);
CREATE UNIQUE INDEX idx_conversation_team_match ON conversation(match_id, party_b_id) WHERE context_type = 'TEAM_MATCH';
CREATE INDEX idx_conversation_party_a ON conversation(party_a_id);
CREATE INDEX idx_conversation_party_b ON conversation(party_b_id);
-- team: tìm đội người gọi đã giải tán (danh sách "Tin nhắn của tôi" — người đã giải tán chỉ xem)
CREATE INDEX idx_team_disbanded_by ON team(disbanded_by) WHERE disbanded_by IS NOT NULL;

-- conversation_message: bên gửi A/B, người thực sự gửi (không mất khi xoá user), trạng thái đọc
CREATE TABLE conversation_message (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL REFERENCES conversation(id) ON DELETE CASCADE,
    sender_party VARCHAR(1) NOT NULL CHECK (sender_party IN ('A', 'B')),
    sender_user_id UUID NOT NULL REFERENCES "user"(id) ON DELETE RESTRICT,
    message TEXT NOT NULL CHECK (char_length(message) BETWEEN 1 AND 1000),
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT (now() AT TIME ZONE 'Asia/Ho_Chi_Minh')
);
CREATE INDEX idx_conversation_message_time ON conversation_message(conversation_id, created_at, id);
CREATE INDEX idx_conversation_message_unread ON conversation_message(conversation_id, sender_party) WHERE NOT is_read;
