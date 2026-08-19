INSERT INTO users (
    id, 
    email, 
    password_hash, 
    full_name, 
    role_id, 
    is_active, 
    requires_password_change, 
    created_at, 
    updated_at
) 
SELECT 
    gen_random_uuid(), 
    'admin@vplmqa.com', 
    '$2a$10$uO9Cc23HzKYTno5l7nGxieRjghc4WktPZnPJYuS5tDIzCYCNg/qou', -- bcrypt hash of 'admin'
    'System Administrator', 
    id, 
    TRUE, 
    TRUE, 
    NOW(), 
    NOW() 
FROM roles 
WHERE name = 'ADMIN';
