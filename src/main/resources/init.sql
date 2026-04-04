CREATE TABLE IF NOT EXISTS notifications (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    type         VARCHAR(10)  NOT NULL COMMENT 'email or sms',
    recipient    VARCHAR(255) NOT NULL,
    subject      VARCHAR(255),
    content      TEXT,
    event_status VARCHAR(10)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING, PROCESSING, or SENT',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_event_status (event_status)
);
