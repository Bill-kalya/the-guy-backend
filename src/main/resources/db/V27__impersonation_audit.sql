-- Impersonation audit is logged via the existing admin_actions table
-- with action_type = 'IMPERSONATE'. No new table needed.

-- Add IMPERSONATE to admin_actions.action_type check constraint if exists
-- (PostgreSQL enum or varchar — existing table uses VARCHAR so no DDL change needed)
