INSERT INTO users (id, tenant_id, email, name, password_hash, role)
VALUES (
    '00000000-0000-0000-0000-000000000002',
    '00000000-0000-0000-0000-000000000001',
    'dev@localhost',
    'Dev Admin',
    '$2a$12$placeholderHashNotUsedForDevUser000000000000000000000000',
    'admin'
) ON CONFLICT DO NOTHING;
