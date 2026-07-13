-- Fix default user passwords: seed used legacy hash for 'password', update to actual passwords
-- admin / admin123
UPDATE users SET password = '$2b$10$1JAz/5LYTCh/Mpn6iwOVu.yyq0SOOQUw9/kOLxbyOVb0zihxmQQ.e'
WHERE username = 'admin' AND password = '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy';

-- operator / operator123
UPDATE users SET password = '$2b$10$cpwvI2mbcEaNLuQhEv9AzOuoqGFbRs4T4DJHam.EVPEErA0E0H1eW'
WHERE username = 'operator' AND password = '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy';
