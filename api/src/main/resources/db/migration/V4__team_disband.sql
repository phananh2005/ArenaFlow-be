-- USER-002: giải tán đội (DISBANDED là trạng thái cuối) + metadata giải tán
ALTER TABLE team DROP CONSTRAINT team_status_check;
ALTER TABLE team ADD CONSTRAINT team_status_check CHECK (status IN ('ACTIVE', 'INACTIVE', 'DISBANDED'));
ALTER TABLE team ADD COLUMN disband_reason VARCHAR(100);
ALTER TABLE team ADD COLUMN disbanded_at TIMESTAMPTZ;
ALTER TABLE team ADD COLUMN disbanded_by UUID REFERENCES "user"(id) ON DELETE RESTRICT;
ALTER TABLE team ADD CONSTRAINT team_disband_consistency_check CHECK (
    (status = 'DISBANDED' AND disband_reason IS NOT NULL AND disbanded_at IS NOT NULL AND disbanded_by IS NOT NULL)
    OR (status <> 'DISBANDED' AND disband_reason IS NULL AND disbanded_at IS NULL AND disbanded_by IS NULL));
