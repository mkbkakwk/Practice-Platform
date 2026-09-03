ALTER TABLE "OfficeDocSubmission"
    ADD COLUMN IF NOT EXISTS judge_version VARCHAR(32) NOT NULL DEFAULT 'legacy',
    ADD COLUMN IF NOT EXISTS result_detail JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN IF NOT EXISTS error_category VARCHAR(64),
    ADD COLUMN IF NOT EXISTS judged_at TIMESTAMP;

-- Rejected documents have a durable FAILED business result but no retained untrusted file.
ALTER TABLE "OfficeDocSubmission"
    ALTER COLUMN student_doc_path DROP NOT NULL;

ALTER TABLE "OfficeDocSubmission"
    DROP CONSTRAINT IF EXISTS ck_office_doc_submission_status;

ALTER TABLE "OfficeDocSubmission"
    ADD CONSTRAINT ck_office_doc_submission_status
        CHECK (status IN (
            'PENDING', 'JUDGING', 'COMPLETED', 'FAILED',
            'AUTO_CHECKED', 'NEEDS_REVIEW', 'REVIEWED'
        )),
    ADD CONSTRAINT ck_office_doc_result_detail_size
        CHECK (octet_length(result_detail::text) <= 262144),
    ADD CONSTRAINT ck_office_doc_judge_version_nonblank
        CHECK (length(trim(judge_version)) > 0);

UPDATE "OfficeDocSubmission"
SET judge_version = 'legacy'
WHERE judge_version IS NULL OR trim(judge_version) = '';

CREATE INDEX IF NOT EXISTS "OfficeDocSubmission_status_created_idx"
    ON "OfficeDocSubmission" (status, created_at DESC);
