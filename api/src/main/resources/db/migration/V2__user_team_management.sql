ALTER TABLE "user" ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER';
ALTER TABLE "user" ADD CONSTRAINT user_role_check CHECK (role IN ('USER', 'ADMIN'));
ALTER TABLE "user" RENAME COLUMN district_id TO ward_id;
DROP INDEX idx_user_district;
CREATE INDEX idx_user_ward ON "user"(ward_id);

ALTER TABLE administrative_unit DROP CONSTRAINT administrative_unit_level_check;
ALTER TABLE administrative_unit ADD CONSTRAINT administrative_unit_level_check CHECK (level IN ('province', 'ward'));
ALTER TABLE administrative_unit ADD CONSTRAINT administrative_unit_ward_parent_check CHECK (level <> 'ward' OR parent_id IS NOT NULL);

ALTER TABLE team ADD COLUMN skill_levels TEXT NOT NULL DEFAULT '[]';
ALTER TABLE team ADD COLUMN inactive_reason VARCHAR(500);
ALTER TABLE team ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE team_member ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE team_member DROP CONSTRAINT team_member_status_check;
ALTER TABLE team_member ADD CONSTRAINT team_member_status_check CHECK (status IN ('PENDING', 'ACTIVE', 'LEFT', 'REJECTED'));
DROP INDEX idx_team_member_active;
CREATE UNIQUE INDEX idx_team_member_pending_or_active ON team_member(team_id, user_id) WHERE status IN ('PENDING', 'ACTIVE');

ALTER TABLE team_captain_history ADD COLUMN reason VARCHAR(500);

DROP TABLE team_tag;

DROP INDEX idx_team_status;
CREATE INDEX idx_team_status_created ON team(status, created_at DESC);
