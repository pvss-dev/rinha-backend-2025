CREATE TABLE payment_requests
(
    correlation_id UUID PRIMARY KEY,
    amount         NUMERIC(15, 2)           NOT NULL,
    status         VARCHAR(20)              NOT NULL,
    processor      VARCHAR(255),
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at     TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_payment_requests_status_created_at ON payment_requests(status, created_at);