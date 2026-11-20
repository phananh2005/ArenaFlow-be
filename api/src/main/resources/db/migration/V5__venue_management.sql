-- Migration V5: Venue management
-- Align venue, venue_image and venue_report with VENUE-001.

-- venue: preserve creator references, optimistic locking, alert gates, type constraints and ward.
ALTER TABLE venue DROP CONSTRAINT venue_created_by_user_id_fkey;
ALTER TABLE venue ADD CONSTRAINT venue_created_by_user_id_fkey
    FOREIGN KEY (created_by_user_id) REFERENCES "user"(id) ON DELETE RESTRICT;
ALTER TABLE venue ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE venue ADD COLUMN stale_alerted_at TIMESTAMPTZ;
ALTER TABLE venue ADD COLUMN closure_alerted_at TIMESTAMPTZ;
UPDATE venue SET venue_type = NULL
WHERE venue_type IS NOT NULL AND venue_type NOT IN ('FIVE_A_SIDE', 'SEVEN_A_SIDE', 'ELEVEN_A_SIDE');
ALTER TABLE venue ADD CONSTRAINT venue_type_check
    CHECK (venue_type IS NULL OR venue_type IN ('FIVE_A_SIDE', 'SEVEN_A_SIDE', 'ELEVEN_A_SIDE'));
UPDATE venue SET admin_verified_inactive_at = COALESCE(admin_verified_inactive_at, updated_at)
WHERE status = 'INACTIVE';
UPDATE venue SET admin_verified_inactive_at = NULL
WHERE status = 'ACTIVE';
ALTER TABLE venue ADD CONSTRAINT venue_inactive_consistency_check CHECK (
    (status = 'INACTIVE' AND admin_verified_inactive_at IS NOT NULL)
    OR (status = 'ACTIVE' AND admin_verified_inactive_at IS NULL)
);
CREATE INDEX idx_venue_status_name ON venue(status, name, id);

-- venue_image: Cloudinary public identifier.
ALTER TABLE venue_image ADD COLUMN public_id VARCHAR(255);
UPDATE venue_image SET public_id = 'legacy/' || id::text WHERE public_id IS NULL;
ALTER TABLE venue_image ALTER COLUMN public_id SET NOT NULL;
CREATE UNIQUE INDEX uq_venue_image_public_id ON venue_image(public_id);

-- venue_report: preserve reporters, add structured lifecycle and prevent duplicate pending reports.
ALTER TABLE venue_report DROP CONSTRAINT venue_report_reporter_id_fkey;
ALTER TABLE venue_report ADD CONSTRAINT venue_report_reporter_id_fkey
    FOREIGN KEY (reporter_id) REFERENCES "user"(id) ON DELETE RESTRICT;
ALTER TABLE venue_report ADD COLUMN detail TEXT;
UPDATE venue_report SET detail = reason, reason = 'OTHER';
ALTER TABLE venue_report ALTER COLUMN reason TYPE VARCHAR(20);
ALTER TABLE venue_report ADD CONSTRAINT venue_report_reason_check
    CHECK (reason IN ('CLOSED', 'WRONG_INFO', 'OTHER'));
ALTER TABLE venue_report ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'PENDING';
ALTER TABLE venue_report ADD CONSTRAINT venue_report_status_check
    CHECK (status IN ('PENDING', 'VERIFIED', 'REJECTED'));
ALTER TABLE venue_report ADD COLUMN handled_by UUID REFERENCES "user"(id) ON DELETE RESTRICT;
ALTER TABLE venue_report ADD COLUMN handled_at TIMESTAMPTZ;
ALTER TABLE venue_report ADD CONSTRAINT venue_report_handled_consistency_check CHECK (
    (status = 'PENDING' AND handled_by IS NULL AND handled_at IS NULL)
    OR (status <> 'PENDING' AND handled_by IS NOT NULL AND handled_at IS NOT NULL)
);
CREATE INDEX idx_venue_report_status_created ON venue_report(status, created_at DESC);
CREATE UNIQUE INDEX uq_venue_report_pending ON venue_report(reporter_id, venue_id, reason)
    WHERE status = 'PENDING';

-- ward stays nullable for legacy rows; API/service enforces it for new and updated venues.
ALTER TABLE venue ADD COLUMN ward_id UUID REFERENCES administrative_unit(id) ON DELETE RESTRICT;
CREATE INDEX idx_venue_ward ON venue(ward_id);
