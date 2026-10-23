UPDATE users SET role = 'USER' WHERE role = 'ADMIN';
ALTER TABLE users DROP CONSTRAINT chk_users_role;
ALTER TABLE users ADD CONSTRAINT chk_users_role CHECK (role = 'USER');
