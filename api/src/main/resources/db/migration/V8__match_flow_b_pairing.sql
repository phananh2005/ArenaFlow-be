-- Luồng B: thương lượng ghép kèo (pairing) mang gói phương án + chi phí + cọc; khoản tự nhập thuộc kèo hoặc pairing.
-- Cần V7 (cost_custom_item.display_order, idx_cost_custom_item_order).

-- match: luồng B ghi venue_id = địa điểm đã chốt khi ghép; cọc chỉ ở luồng B; bỏ 2 cột chốt của V1 (không dùng)
ALTER TABLE match DROP CONSTRAINT match_check2;
ALTER TABLE match ADD CONSTRAINT match_type_venue_check CHECK (
    (type = 'HAS_VENUE' AND venue_id IS NOT NULL)
    OR (type = 'NEED_VENUE' AND (venue_id IS NULL) = (confirmed_opponent_team_id IS NULL)));
ALTER TABLE match DROP COLUMN confirmed_venue_id;
ALTER TABLE match DROP COLUMN confirmed_match_time;
ALTER TABLE match ADD COLUMN cost_deposit_amount BIGINT;
ALTER TABLE match ADD CONSTRAINT match_deposit_check CHECK (
    (cost_deposit_amount IS NULL OR cost_deposit_amount > 0)
    AND (cost_deposit_amount IS NOT NULL OR cost_deposit_holder IS NULL)
    AND (type = 'NEED_VENUE' OR cost_deposit_amount IS NULL));
DROP INDEX idx_match_search_gps;   -- search_lat/lng không dùng (kèo B tìm theo lựa chọn địa điểm)

-- match_venue_option: thứ tự nhập, không trùng địa điểm; đích FK của phương án
ALTER TABLE match_venue_option ADD COLUMN display_order INTEGER;
UPDATE match_venue_option o SET display_order = r.rn
    FROM (SELECT id, ROW_NUMBER() OVER (PARTITION BY match_id ORDER BY created_at, id) AS rn FROM match_venue_option) r
    WHERE o.id = r.id;
ALTER TABLE match_venue_option ALTER COLUMN display_order SET NOT NULL;
ALTER TABLE match_venue_option ADD CONSTRAINT match_venue_option_order_check CHECK (display_order BETWEEN 1 AND 5);
ALTER TABLE match_venue_option ADD CONSTRAINT match_venue_option_match_venue_key UNIQUE (match_id, venue_id);
ALTER TABLE match_venue_option ADD CONSTRAINT match_venue_option_match_order_key UNIQUE (match_id, display_order);
DROP INDEX idx_match_venue_option_match;
CREATE INDEX idx_match_venue_option_venue ON match_venue_option(venue_id);

-- match_time_option: khoảng hợp lệ, không trùng giờ bắt đầu; đích FK của phương án
ALTER TABLE match_time_option ADD CONSTRAINT match_time_option_range_check CHECK (proposed_end_time > proposed_start_time);
ALTER TABLE match_time_option ADD CONSTRAINT match_time_option_match_id_key UNIQUE (match_id, id);
ALTER TABLE match_time_option ADD CONSTRAINT match_time_option_match_start_key UNIQUE (match_id, proposed_start_time);
DROP INDEX idx_match_time_option_match;

-- pairing: gói thương lượng (phương án + chi phí + cọc), revision, updated_at
ALTER TABLE pairing ADD COLUMN revision INTEGER NOT NULL DEFAULT 0;
ALTER TABLE pairing ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT (now() AT TIME ZONE 'Asia/Ho_Chi_Minh');
ALTER TABLE pairing ADD COLUMN proposed_venue_id UUID;
ALTER TABLE pairing ADD COLUMN proposed_time_option_id UUID;
ALTER TABLE pairing ADD COLUMN cost_type VARCHAR(50);
ALTER TABLE pairing ADD COLUMN cost_split VARCHAR(50);
ALTER TABLE pairing ADD COLUMN cost_venue_amount BIGINT;
ALTER TABLE pairing ADD COLUMN cost_drinks_amount BIGINT;
ALTER TABLE pairing ADD COLUMN cost_deposit_amount BIGINT;
ALTER TABLE pairing ADD COLUMN cost_deposit_holder VARCHAR(100);
ALTER TABLE pairing ADD CONSTRAINT pairing_proposed_venue_fkey
    FOREIGN KEY (match_id, proposed_venue_id) REFERENCES match_venue_option(match_id, venue_id);
ALTER TABLE pairing ADD CONSTRAINT pairing_proposed_time_fkey
    FOREIGN KEY (match_id, proposed_time_option_id) REFERENCES match_time_option(match_id, id);
ALTER TABLE pairing ADD CONSTRAINT pairing_proposal_pair_check
    CHECK ((proposed_venue_id IS NULL) = (proposed_time_option_id IS NULL));
ALTER TABLE pairing ADD CONSTRAINT pairing_confirmed_proposal_check
    CHECK (status <> 'CONFIRMED' OR proposed_venue_id IS NOT NULL);
ALTER TABLE pairing ADD CONSTRAINT pairing_revision_check CHECK (revision >= 0);
ALTER TABLE pairing ADD CONSTRAINT pairing_cost_type_check
    CHECK (cost_type IS NULL OR cost_type IN ('VENUE_ONLY', 'DRINKS_ONLY', 'BOTH'));
ALTER TABLE pairing ADD CONSTRAINT pairing_cost_split_check
    CHECK (cost_split IS NULL OR cost_split IN ('FIFTY_FIFTY', 'SEVENTY_THIRTY_HOME', 'SEVENTY_THIRTY_LOSER', 'LOSER_PAYS_DRINKS'));
ALTER TABLE pairing ADD CONSTRAINT pairing_cost_amount_check CHECK (
    (cost_venue_amount IS NULL OR cost_venue_amount > 0) AND (cost_drinks_amount IS NULL OR cost_drinks_amount > 0)
    AND (cost_deposit_amount IS NULL OR cost_deposit_amount > 0)
    AND (cost_deposit_amount IS NOT NULL OR cost_deposit_holder IS NULL));
DROP INDEX idx_pairing_race;
CREATE UNIQUE INDEX idx_pairing_confirmed ON pairing(match_id) WHERE status = 'CONFIRMED';

-- cost_custom_item: thuộc kèo (luồng A) hoặc thuộc pairing (luồng B)
ALTER TABLE cost_custom_item ALTER COLUMN match_id DROP NOT NULL;
ALTER TABLE cost_custom_item ADD COLUMN pairing_id UUID;
ALTER TABLE cost_custom_item ADD CONSTRAINT cost_custom_item_pairing_fkey
    FOREIGN KEY (pairing_id) REFERENCES pairing(id) ON DELETE CASCADE;
ALTER TABLE cost_custom_item ADD CONSTRAINT cost_custom_item_owner_check CHECK ((match_id IS NULL) <> (pairing_id IS NULL));
CREATE UNIQUE INDEX idx_cost_custom_item_pairing_order ON cost_custom_item(pairing_id, display_order);
