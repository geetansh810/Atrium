-- V11 (M4.1): review_required flag on role_definitions (03 §V1 amendment,
-- 15 §0). A role flagged true structurally blocks its assigned tasks from
-- ever reaching 'approved' except via a named human user — see
-- TaskService.approve / 08-conventions.md security rule 8.

ALTER TABLE role_definitions ADD COLUMN review_required BOOLEAN NOT NULL DEFAULT false;
