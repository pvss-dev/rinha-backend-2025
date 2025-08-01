CREATE TABLE payments
(
    id            BIGSERIAL PRIMARY KEY,
    correlationId UUID UNIQUE    NOT NULL,
    amount        DECIMAL(10, 2) NOT NULL,
    processor     VARCHAR(20),
    status        VARCHAR(20)    NOT NULL,
    createdAt     TIMESTAMP      NOT NULL,
    updatedAt     TIMESTAMP
);

CREATE INDEX idx_payments_status ON payments (status) WHERE status = 'PENDING';

CREATE INDEX idx_payments_created_at ON payments (createdAt);