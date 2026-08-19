UPDATE users
SET password_hash = '$2a$10$uO9Cc23HzKYTno5l7nGxieRjghc4WktPZnPJYuS5tDIzCYCNg/qou',
    is_active = TRUE,
    requires_password_change = FALSE,
    updated_at = NOW()
WHERE email = 'admin@vplmqa.com';
