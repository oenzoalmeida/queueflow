-- forced first-login password change: set at provisioning time (temp password), cleared by /api/auth/change-password
ALTER TABLE users ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;
