CREATE TABLE payments
(
    id             BIGSERIAL PRIMARY KEY,
    correlation_id UUID UNIQUE    NOT NULL,
    amount         DECIMAL(10, 2) NOT NULL,
    processor      VARCHAR(20),
    status         VARCHAR(20)    NOT NULL,
    created_at     TIMESTAMP      NOT NULL,
    updated_at     TIMESTAMP
);

CREATE INDEX idx_payments_status ON payments (status) WHERE status = 'PENDING';

CREATE INDEX idx_payments_created_at ON payments (created_at);