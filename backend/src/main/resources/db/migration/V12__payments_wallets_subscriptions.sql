-- Paiements, portefeuilles, retraits et abonnements (phase 6)
-- Montants en NUMERIC(14,2), devise XOF. Le grand livre des portefeuilles est en ajout seul.

CREATE TABLE wallets (
    id         UUID PRIMARY KEY,
    owner_type VARCHAR(10)   NOT NULL,
    owner_id   UUID,
    currency   VARCHAR(3)    NOT NULL DEFAULT 'XOF',
    balance    NUMERIC(14, 2) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ   NOT NULL,
    updated_at TIMESTAMPTZ   NOT NULL,
    created_by UUID,
    updated_by UUID,
    version    BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ck_wallet_owner_type CHECK (owner_type IN ('USER', 'PHARMACY', 'PLATFORM')),
    CONSTRAINT ck_wallet_owner_id CHECK ((owner_type = 'PLATFORM') = (owner_id IS NULL)),
    CONSTRAINT ck_wallet_balance CHECK (balance >= 0)
);
CREATE UNIQUE INDEX ux_wallet_owner ON wallets (owner_type, owner_id) WHERE owner_id IS NOT NULL;
CREATE UNIQUE INDEX ux_wallet_platform ON wallets (owner_type) WHERE owner_id IS NULL;

-- Portefeuille de la plateforme : reçoit les commissions et les abonnements.
INSERT INTO wallets (id, owner_type, owner_id, currency, balance, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-0000000000f1', 'PLATFORM', NULL, 'XOF', 0, now(), now());

CREATE TABLE payments (
    id                UUID PRIMARY KEY,
    payer_id          UUID           NOT NULL REFERENCES users (id),
    payable_type      VARCHAR(20)    NOT NULL,
    payable_id        UUID           NOT NULL,
    method            VARCHAR(15)    NOT NULL,
    provider          VARCHAR(30)    NOT NULL,
    provider_ref      VARCHAR(100),
    status            VARCHAR(10)    NOT NULL,
    amount            NUMERIC(14, 2) NOT NULL,
    currency          VARCHAR(3)     NOT NULL DEFAULT 'XOF',
    commission_rate   NUMERIC(5, 4)  NOT NULL DEFAULT 0,
    commission_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
    net_amount        NUMERIC(14, 2) NOT NULL,
    payee_type        VARCHAR(10)    NOT NULL,
    payee_id          UUID,
    phone             VARCHAR(30),
    idempotency_key   VARCHAR(64),
    failure_reason    VARCHAR(255),
    captured_at       TIMESTAMPTZ,
    released_at       TIMESTAMPTZ,
    refunded_at       TIMESTAMPTZ,
    created_at        TIMESTAMPTZ    NOT NULL,
    updated_at        TIMESTAMPTZ    NOT NULL,
    created_by        UUID,
    updated_by        UUID,
    version           BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT ck_payment_type CHECK (payable_type IN ('APPOINTMENT', 'PHARMACY_ORDER', 'SUBSCRIPTION', 'WALLET_TOPUP')),
    CONSTRAINT ck_payment_method CHECK (method IN ('MOBILE_MONEY', 'CARD', 'BANK_TRANSFER', 'WALLET')),
    CONSTRAINT ck_payment_status CHECK (status IN ('PENDING', 'FAILED', 'CAPTURED', 'RELEASED', 'REFUNDED')),
    CONSTRAINT ck_payment_payee CHECK (payee_type IN ('USER', 'PHARMACY', 'PLATFORM')
                                       AND (payee_type = 'PLATFORM') = (payee_id IS NULL)),
    CONSTRAINT ck_payment_amounts CHECK (amount > 0 AND commission_amount >= 0 AND net_amount >= 0
                                         AND commission_amount + net_amount = amount),
    CONSTRAINT ck_payment_rate CHECK (commission_rate >= 0 AND commission_rate <= 1)
);
-- Un objet à payer n'a qu'un seul paiement vivant : pas de double paiement, même en cas de requêtes concurrentes.
CREATE UNIQUE INDEX ux_payment_live ON payments (payable_type, payable_id)
    WHERE status IN ('PENDING', 'CAPTURED', 'RELEASED');
CREATE UNIQUE INDEX ux_payment_idem ON payments (payer_id, idempotency_key) WHERE idempotency_key IS NOT NULL;
CREATE INDEX ix_payments_payer ON payments (payer_id, created_at DESC);
CREATE INDEX ix_payments_status ON payments (status, created_at DESC);
CREATE INDEX ix_payments_payee ON payments (payee_type, payee_id);

CREATE TABLE withdrawals (
    id            UUID PRIMARY KEY,
    wallet_id     UUID           NOT NULL REFERENCES wallets (id),
    user_id       UUID           NOT NULL REFERENCES users (id),
    amount        NUMERIC(14, 2) NOT NULL,
    currency      VARCHAR(3)     NOT NULL DEFAULT 'XOF',
    method        VARCHAR(15)    NOT NULL,
    destination   TEXT           NOT NULL, -- chiffré (numéro Mobile Money ou IBAN)
    status        VARCHAR(10)    NOT NULL,
    provider_ref  VARCHAR(100),
    processed_by  UUID REFERENCES users (id),
    processed_at  TIMESTAMPTZ,
    reject_reason VARCHAR(500),
    created_at    TIMESTAMPTZ    NOT NULL,
    updated_at    TIMESTAMPTZ    NOT NULL,
    created_by    UUID,
    updated_by    UUID,
    version       BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT ck_withdrawal_amount CHECK (amount > 0),
    CONSTRAINT ck_withdrawal_method CHECK (method IN ('MOBILE_MONEY', 'BANK_TRANSFER')),
    CONSTRAINT ck_withdrawal_status CHECK (status IN ('REQUESTED', 'PAID', 'REJECTED')),
    CONSTRAINT ck_withdrawal_processed CHECK ((status = 'REQUESTED') = (processed_at IS NULL))
);
CREATE INDEX ix_withdrawals_user ON withdrawals (user_id, created_at DESC);
CREATE INDEX ix_withdrawals_status ON withdrawals (status, created_at);

CREATE TABLE wallet_transactions (
    id            UUID PRIMARY KEY,
    wallet_id     UUID           NOT NULL REFERENCES wallets (id),
    type          VARCHAR(6)     NOT NULL,
    reason        VARCHAR(25)    NOT NULL,
    amount        NUMERIC(14, 2) NOT NULL,
    balance_after NUMERIC(14, 2) NOT NULL,
    payment_id    UUID REFERENCES payments (id),
    withdrawal_id UUID REFERENCES withdrawals (id),
    description   VARCHAR(255),
    created_at    TIMESTAMPTZ    NOT NULL,
    CONSTRAINT ck_wtx_type CHECK (type IN ('CREDIT', 'DEBIT')),
    CONSTRAINT ck_wtx_reason CHECK (reason IN ('TOPUP', 'PAYMENT', 'REFUND', 'EARNING', 'COMMISSION', 'SUBSCRIPTION',
                                               'WITHDRAWAL', 'WITHDRAWAL_REVERSAL')),
    CONSTRAINT ck_wtx_amount CHECK (amount > 0 AND balance_after >= 0)
);
CREATE INDEX ix_wtx_wallet ON wallet_transactions (wallet_id, created_at DESC);
CREATE INDEX ix_wtx_payment ON wallet_transactions (payment_id) WHERE payment_id IS NOT NULL;

CREATE FUNCTION wallet_transactions_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'wallet_transactions est en ajout seul';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_wallet_transactions_immutable
    BEFORE UPDATE OR DELETE ON wallet_transactions
    FOR EACH ROW EXECUTE FUNCTION wallet_transactions_immutable();

-- Catalogue des abonnements. Prix indicatifs, modifiables par l'administration (FINANCE/SUPER).
CREATE TABLE subscription_plans (
    id              UUID PRIMARY KEY,
    code            VARCHAR(30)    NOT NULL UNIQUE,
    audience        VARCHAR(12)    NOT NULL,
    name            VARCHAR(100)   NOT NULL,
    description     VARCHAR(500),
    price           NUMERIC(14, 2) NOT NULL,
    currency        VARCHAR(3)     NOT NULL DEFAULT 'XOF',
    duration_days   INTEGER        NOT NULL,
    commission_rate NUMERIC(5, 4),  -- surcharge du taux de commission pour les professionnels abonnés
    active          BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ    NOT NULL,
    updated_at      TIMESTAMPTZ    NOT NULL,
    created_by      UUID,
    updated_by      UUID,
    version         BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT ck_plan_audience CHECK (audience IN ('PROFESSIONAL', 'PATIENT')),
    CONSTRAINT ck_plan_price CHECK (price >= 0),
    CONSTRAINT ck_plan_duration CHECK (duration_days > 0),
    CONSTRAINT ck_plan_rate CHECK (commission_rate IS NULL OR (commission_rate >= 0 AND commission_rate <= 1))
);

INSERT INTO subscription_plans (id, code, audience, name, description, price, duration_days, commission_rate, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-0000000000a1', 'PRO_STANDARD', 'PROFESSIONAL', 'Professionnel Standard',
        'Présence dans le répertoire et prise de rendez-vous.', 5000, 30, NULL, now(), now()),
       ('00000000-0000-0000-0000-0000000000a2', 'PRO_PREMIUM', 'PROFESSIONAL', 'Professionnel Premium',
        'Commission réduite sur les consultations.', 15000, 30, 0.0500, now(), now()),
       ('00000000-0000-0000-0000-0000000000a3', 'PATIENT_PREMIUM', 'PATIENT', 'Patient Premium',
        'Avantages Premium pour le patient.', 2000, 30, NULL, now(), now());

-- Achat d'une période d'abonnement : c'est l'objet payé (prix et durée figés au moment de la commande).
CREATE TABLE subscription_orders (
    id            UUID PRIMARY KEY,
    user_id       UUID           NOT NULL REFERENCES users (id),
    plan_id       UUID           NOT NULL REFERENCES subscription_plans (id),
    price         NUMERIC(14, 2) NOT NULL,
    currency      VARCHAR(3)     NOT NULL DEFAULT 'XOF',
    duration_days INTEGER        NOT NULL,
    created_at    TIMESTAMPTZ    NOT NULL,
    updated_at    TIMESTAMPTZ    NOT NULL,
    created_by    UUID,
    updated_by    UUID,
    version       BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT ck_sub_order_price CHECK (price > 0),
    CONSTRAINT ck_sub_order_duration CHECK (duration_days > 0)
);
CREATE INDEX ix_sub_orders_user ON subscription_orders (user_id, created_at DESC);

CREATE TABLE subscriptions (
    id           UUID PRIMARY KEY,
    user_id      UUID        NOT NULL REFERENCES users (id),
    plan_id      UUID        NOT NULL REFERENCES subscription_plans (id),
    status       VARCHAR(10) NOT NULL,
    starts_at    TIMESTAMPTZ NOT NULL,
    ends_at      TIMESTAMPTZ NOT NULL,
    payment_id   UUID REFERENCES payments (id),
    cancelled_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL,
    created_by   UUID,
    updated_by   UUID,
    version      BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_subscription_status CHECK (status IN ('TRIAL', 'ACTIVE', 'EXPIRED', 'CANCELLED')),
    CONSTRAINT ck_subscription_period CHECK (ends_at > starts_at)
);
-- Un seul abonnement vivant par utilisateur.
CREATE UNIQUE INDEX ux_subscription_live ON subscriptions (user_id) WHERE status IN ('TRIAL', 'ACTIVE');
CREATE INDEX ix_subscriptions_user ON subscriptions (user_id, ends_at DESC);
CREATE INDEX ix_subscriptions_ends ON subscriptions (ends_at) WHERE status IN ('TRIAL', 'ACTIVE');
