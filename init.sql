CREATE TABLE payments
(
    id            SERIAL PRIMARY KEY,
    correlationId UUID UNIQUE    NOT NULL,
    amount        DECIMAL(10, 2) NOT NULL,
    processor     VARCHAR(20),
    status        VARCHAR(20)    NOT NULL,
    createdAt     TIMESTAMP      NOT NULL,
    updatedAt     TIMESTAMP
);

-- ÍNDICE CRÍTICO PARA A PERFORMANCE DO WORKER!
CREATE INDEX idx_payments_status ON payments (status) WHERE status = 'PENDING';