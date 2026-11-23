-- Mọi thời điểm chuyển sang giờ Việt Nam không kèm múi giờ (timestamp có múi giờ → TIMESTAMP).
-- USING col AT TIME ZONE 'Asia/Ho_Chi_Minh' giữ đúng thời điểm đã lưu (12:00 UTC → 19:00) và không phụ thuộc
-- TimeZone của phiên. Mỗi bảng một câu ALTER TABLE để CHECK so hai cột được kiểm sau khi mọi cột cùng đổi kiểu.
-- DEFAULT tính giờ Việt Nam tường minh: hàm thời gian không kèm AT TIME ZONE đổi theo TimeZone phiên
-- (Neon mặc định GMT) → lệch 7 giờ.
-- NEVER chạy tay lại file này: cột đã là TIMESTAMP thì AT TIME ZONE làm mỗi giá trị lùi 7 giờ.

ALTER TABLE administrative_unit
    ALTER COLUMN created_at DROP DEFAULT,
    ALTER COLUMN created_at TYPE TIMESTAMP USING created_at AT TIME ZONE 'Asia/Ho_Chi_Minh',
    ALTER COLUMN created_at SET DEFAULT (now() AT TIME ZONE 'Asia/Ho_Chi_Minh');

ALTER TABLE "user"
    ALTER COLUMN created_at DROP DEFAULT,
    ALTER COLUMN updated_at DROP DEFAULT,
    ALTER COLUMN created_at TYPE TIMESTAMP USING created_at AT TIME ZONE 'Asia/Ho_Chi_Minh',
    ALTER COLUMN updated_at TYPE TIMESTAMP USING updated_at AT TIME ZONE 'Asia/Ho_Chi_Minh',
    ALTER COLUMN deleted_at TYPE TIMESTAMP USING deleted_at AT TIME ZONE 'Asia/Ho_Chi_Minh',
    ALTER COLUMN created_at SET DEFAULT (now() AT TIME ZONE 'Asia/Ho_Chi_Minh'),
    ALTER COLUMN updated_at SET DEFAULT (now() AT TIME ZONE 'Asia/Ho_Chi_Minh');

ALTER TABLE oauth_account
    ALTER COLUMN created_at DROP DEFAULT,
    ALTER COLUMN created_at TYPE TIMESTAMP USING created_at AT TIME ZONE 'Asia/Ho_Chi_Minh',
    ALTER COLUMN created_at SET DEFAULT (now() AT TIME ZONE 'Asia/Ho_Chi_Minh');

ALTER TABLE team
    ALTER COLUMN created_at DROP DEFAULT,
    ALTER COLUMN updated_at DROP DEFAULT,
    ALTER COLUMN created_at TYPE TIMESTAMP USING created_at AT TIME ZONE 'Asia/Ho_Chi_Minh',
    ALTER COLUMN updated_at TYPE TIMESTAMP USING updated_at AT TIME ZONE 'Asia/Ho_Chi_Minh',
    ALTER COLUMN disbanded_at TYPE TIMESTAMP USING disbanded_at AT TIME ZONE 'Asia/Ho_Chi_Minh',
    ALTER COLUMN created_at SET DEFAULT (now() AT TIME ZONE 'Asia/Ho_Chi_Minh'),
    ALTER COLUMN updated_at SET DEFAULT (now() AT TIME ZONE 'Asia/Ho_Chi_Minh');

ALTER TABLE team_member
    ALTER COLUMN invited_at DROP DEFAULT,
    ALTER COLUMN invited_at TYPE TIMESTAMP USING invited_at AT TIME ZONE 'Asia/Ho_Chi_Minh',
    ALTER COLUMN joined_at TYPE TIMESTAMP USING joined_at AT TIME ZONE 'Asia/Ho_Chi_Minh',
    ALTER COLUMN left_at TYPE TIMESTAMP USING left_at AT TIME ZONE 'Asia/Ho_Chi_Minh',
    ALTER COLUMN invited_at SET DEFAULT (now() AT TIME ZONE 'Asia/Ho_Chi_Minh');

ALTER TABLE team_captain_history
    ALTER COLUMN transferred_at DROP DEFAULT,
    ALTER COLUMN transferred_at TYPE TIMESTAMP USING transferred_at AT TIME ZONE 'Asia/Ho_Chi_Minh',
    ALTER COLUMN transferred_at SET DEFAULT (now() AT TIME ZONE 'Asia/Ho_Chi_Minh');

ALTER TABLE sport
    ALTER COLUMN created_at DROP DEFAULT,
    ALTER COLUMN created_at TYPE TIMESTAMP USING created_at AT TIME ZONE 'Asia/Ho_Chi_Minh',
    ALTER COLUMN created_at SET DEFAULT (now() AT TIME ZONE 'Asia/Ho_Chi_Minh');

ALTER TABLE venue
    ALTER COLUMN created_at DROP DEFAULT,
    ALTER COLUMN updated_at DROP DEFAULT,
    ALTER COLUMN last_selected_at TYPE TIMESTAMP USING last_selected_at AT TIME ZONE 'Asia/Ho_Chi_Minh',
    ALTER COLUMN admin_verified_inactive_at TYPE TIMESTAMP USING admin_verified_inactive_at AT TIME ZONE 'Asia/Ho_Chi_Minh',
    ALTER COLUMN stale_alerted_at TYPE TIMESTAMP USING stale_alerted_at AT TIME ZONE 'Asia/Ho_Chi_Minh',
    ALTER COLUMN closure_alerted_at TYPE TIMESTAMP USING closure_alerted_at AT TIME ZONE 'Asia/Ho_Chi_Minh',
    ALTER COLUMN created_at TYPE TIMESTAMP USING created_at AT TIME ZONE 'Asia/Ho_Chi_Minh',
    ALTER COLUMN updated_at TYPE TIMESTAMP USING updated_at AT TIME ZONE 'Asia/Ho_Chi_Minh',
    ALTER COLUMN created_at SET DEFAULT (now() AT TIME ZONE 'Asia/Ho_Chi_Minh'),
    ALTER COLUMN updated_at SET DEFAULT (now() AT TIME ZONE 'Asia/Ho_Chi_Minh');

ALTER TABLE venue_image
    ALTER COLUMN created_at DROP DEFAULT,
    ALTER COLUMN created_at TYPE TIMESTAMP USING created_at AT TIME ZONE 'Asia/Ho_Chi_Minh',
    ALTER COLUMN created_at SET DEFAULT (now() AT TIME ZONE 'Asia/Ho_Chi_Minh');

ALTER TABLE venue_report
    ALTER COLUMN created_at DROP DEFAULT,
    ALTER COLUMN handled_at TYPE TIMESTAMP USING handled_at AT TIME ZONE 'Asia/Ho_Chi_Minh',
    ALTER COLUMN created_at TYPE TIMESTAMP USING created_at AT TIME ZONE 'Asia/Ho_Chi_Minh',
    ALTER COLUMN created_at SET DEFAULT (now() AT TIME ZONE 'Asia/Ho_Chi_Minh');

ALTER TABLE match
    ALTER COLUMN created_at DROP DEFAULT,
    ALTER COLUMN updated_at DROP DEFAULT,
    ALTER COLUMN match_start_time TYPE TIMESTAMP USING match_start_time AT TIME ZONE 'Asia/Ho_Chi_Minh',
    ALTER COLUMN match_end_time TYPE TIMESTAMP USING match_end_time AT TIME ZONE 'Asia/Ho_Chi_Minh',
    ALTER COLUMN expires_at TYPE TIMESTAMP USING expires_at AT TIME ZONE 'Asia/Ho_Chi_Minh',
    ALTER COLUMN confirmed_match_time TYPE TIMESTAMP USING confirmed_match_time AT TIME ZONE 'Asia/Ho_Chi_Minh',
    ALTER COLUMN cancelled_at TYPE TIMESTAMP USING cancelled_at AT TIME ZONE 'Asia/Ho_Chi_Minh',
    ALTER COLUMN created_at TYPE TIMESTAMP USING created_at AT TIME ZONE 'Asia/Ho_Chi_Minh',
    ALTER COLUMN updated_at TYPE TIMESTAMP USING updated_at AT TIME ZONE 'Asia/Ho_Chi_Minh',
    ALTER COLUMN created_at SET DEFAULT (now() AT TIME ZONE 'Asia/Ho_Chi_Minh'),
    ALTER COLUMN updated_at SET DEFAULT (now() AT TIME ZONE 'Asia/Ho_Chi_Minh');

ALTER TABLE match_venue_option
    ALTER COLUMN created_at DROP DEFAULT,
    ALTER COLUMN created_at TYPE TIMESTAMP USING created_at AT TIME ZONE 'Asia/Ho_Chi_Minh',
    ALTER COLUMN created_at SET DEFAULT (now() AT TIME ZONE 'Asia/Ho_Chi_Minh');

ALTER TABLE match_time_option
    ALTER COLUMN created_at DROP DEFAULT,
    ALTER COLUMN proposed_start_time TYPE TIMESTAMP USING proposed_start_time AT TIME ZONE 'Asia/Ho_Chi_Minh',
    ALTER COLUMN proposed_end_time TYPE TIMESTAMP USING proposed_end_time AT TIME ZONE 'Asia/Ho_Chi_Minh',
    ALTER COLUMN created_at TYPE TIMESTAMP USING created_at AT TIME ZONE 'Asia/Ho_Chi_Minh',
    ALTER COLUMN created_at SET DEFAULT (now() AT TIME ZONE 'Asia/Ho_Chi_Minh');

ALTER TABLE cost_custom_item
    ALTER COLUMN created_at DROP DEFAULT,
    ALTER COLUMN created_at TYPE TIMESTAMP USING created_at AT TIME ZONE 'Asia/Ho_Chi_Minh',
    ALTER COLUMN created_at SET DEFAULT (now() AT TIME ZONE 'Asia/Ho_Chi_Minh');

ALTER TABLE match_request
    ALTER COLUMN requested_at DROP DEFAULT,
    ALTER COLUMN requested_at TYPE TIMESTAMP USING requested_at AT TIME ZONE 'Asia/Ho_Chi_Minh',
    ALTER COLUMN responded_at TYPE TIMESTAMP USING responded_at AT TIME ZONE 'Asia/Ho_Chi_Minh',
    ALTER COLUMN withdrawn_at TYPE TIMESTAMP USING withdrawn_at AT TIME ZONE 'Asia/Ho_Chi_Minh',
    ALTER COLUMN requested_at SET DEFAULT (now() AT TIME ZONE 'Asia/Ho_Chi_Minh');

ALTER TABLE pairing
    ALTER COLUMN created_at DROP DEFAULT,
    ALTER COLUMN author_confirmed_at TYPE TIMESTAMP USING author_confirmed_at AT TIME ZONE 'Asia/Ho_Chi_Minh',
    ALTER COLUMN interested_confirmed_at TYPE TIMESTAMP USING interested_confirmed_at AT TIME ZONE 'Asia/Ho_Chi_Minh',
    ALTER COLUMN final_confirmed_at TYPE TIMESTAMP USING final_confirmed_at AT TIME ZONE 'Asia/Ho_Chi_Minh',
    ALTER COLUMN created_at TYPE TIMESTAMP USING created_at AT TIME ZONE 'Asia/Ho_Chi_Minh',
    ALTER COLUMN created_at SET DEFAULT (now() AT TIME ZONE 'Asia/Ho_Chi_Minh');

ALTER TABLE rating
    ALTER COLUMN created_at DROP DEFAULT,
    ALTER COLUMN created_at TYPE TIMESTAMP USING created_at AT TIME ZONE 'Asia/Ho_Chi_Minh',
    ALTER COLUMN created_at SET DEFAULT (now() AT TIME ZONE 'Asia/Ho_Chi_Minh');

ALTER TABLE report
    ALTER COLUMN created_at DROP DEFAULT,
    ALTER COLUMN admin_reviewed_at TYPE TIMESTAMP USING admin_reviewed_at AT TIME ZONE 'Asia/Ho_Chi_Minh',
    ALTER COLUMN created_at TYPE TIMESTAMP USING created_at AT TIME ZONE 'Asia/Ho_Chi_Minh',
    ALTER COLUMN created_at SET DEFAULT (now() AT TIME ZONE 'Asia/Ho_Chi_Minh');

ALTER TABLE mercenary_recruitment
    ALTER COLUMN created_at DROP DEFAULT,
    ALTER COLUMN updated_at DROP DEFAULT,
    ALTER COLUMN created_at TYPE TIMESTAMP USING created_at AT TIME ZONE 'Asia/Ho_Chi_Minh',
    ALTER COLUMN updated_at TYPE TIMESTAMP USING updated_at AT TIME ZONE 'Asia/Ho_Chi_Minh',
    ALTER COLUMN created_at SET DEFAULT (now() AT TIME ZONE 'Asia/Ho_Chi_Minh'),
    ALTER COLUMN updated_at SET DEFAULT (now() AT TIME ZONE 'Asia/Ho_Chi_Minh');

ALTER TABLE mercenary_application
    ALTER COLUMN applied_at DROP DEFAULT,
    ALTER COLUMN applied_at TYPE TIMESTAMP USING applied_at AT TIME ZONE 'Asia/Ho_Chi_Minh',
    ALTER COLUMN responded_at TYPE TIMESTAMP USING responded_at AT TIME ZONE 'Asia/Ho_Chi_Minh',
    ALTER COLUMN withdrawn_at TYPE TIMESTAMP USING withdrawn_at AT TIME ZONE 'Asia/Ho_Chi_Minh',
    ALTER COLUMN applied_at SET DEFAULT (now() AT TIME ZONE 'Asia/Ho_Chi_Minh');

ALTER TABLE chat_message
    ALTER COLUMN created_at DROP DEFAULT,
    ALTER COLUMN created_at TYPE TIMESTAMP USING created_at AT TIME ZONE 'Asia/Ho_Chi_Minh',
    ALTER COLUMN created_at SET DEFAULT (now() AT TIME ZONE 'Asia/Ho_Chi_Minh');

ALTER TABLE notification
    ALTER COLUMN created_at DROP DEFAULT,
    ALTER COLUMN created_at TYPE TIMESTAMP USING created_at AT TIME ZONE 'Asia/Ho_Chi_Minh',
    ALTER COLUMN created_at SET DEFAULT (now() AT TIME ZONE 'Asia/Ho_Chi_Minh');
