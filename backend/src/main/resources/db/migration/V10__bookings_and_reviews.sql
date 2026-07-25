-- Agregado "schedule" / bookings (ARQUITETURA §13) e "reviews" (§4.6, §9.2).
CREATE TABLE booking (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    proposal_id      UUID NOT NULL REFERENCES proposal (id) ON DELETE RESTRICT,
    scheduled_start  TIMESTAMPTZ,
    scheduled_end    TIMESTAMPTZ,
    status           VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED'
                         CHECK (status IN ('CONFIRMED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'NO_SHOW')),
    completed_at     TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- completeBooking (§11.2) preenche completed_at exatamente ao transitar
    -- para COMPLETED; sustenta a pré-condição de review mais abaixo.
    CONSTRAINT chk_booking_completed_at CHECK (
        (status = 'COMPLETED' AND completed_at IS NOT NULL) OR (status <> 'COMPLETED')
    )
);

-- Uma proposta aceite dá origem no máximo a uma marcação.
CREATE UNIQUE INDEX uq_booking_proposal_id ON booking (proposal_id);
CREATE INDEX idx_booking_status ON booking (status);

CREATE TRIGGER trg_booking_set_updated_at
    BEFORE UPDATE ON booking
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE review (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id  UUID NOT NULL REFERENCES booking (id) ON DELETE RESTRICT,
    author_id   UUID NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    target_id   UUID NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    rating      SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment     VARCHAR(2000),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_review_author_not_target CHECK (author_id <> target_id)
);

-- Decisão de modelação: ARQUITETURA §9.2 lista "booking_id (único)", mas
-- §4.6 exige avaliação bilateral (cliente avalia prestador e vice-versa) e o
-- 409 do contrato ("Já avaliado ou marcação não concluída") é por autor.
-- UNIQUE(booking_id, author_id) satisfaz as duas leituras: permite as duas
-- direções da avaliação bilateral e impede duplicar a mesma pela mesma
-- pessoa.
CREATE UNIQUE INDEX uq_review_booking_author ON review (booking_id, author_id);
CREATE INDEX idx_review_target_id ON review (target_id);

-- Impede review sem booking concluído associado (requisito explícito desta
-- migração). Um CHECK simples não alcança outra tabela; a integridade
-- referencial condicional exige um trigger, que é a forma correta de a
-- expressar em PostgreSQL sem sair do schema para a aplicação.
CREATE OR REPLACE FUNCTION enforce_review_requires_completed_booking()
RETURNS TRIGGER
LANGUAGE plpgsql AS $$
DECLARE
    v_booking_status VARCHAR(20);
BEGIN
    SELECT status INTO v_booking_status FROM booking WHERE id = NEW.booking_id;
    IF v_booking_status IS DISTINCT FROM 'COMPLETED' THEN
        RAISE EXCEPTION 'review requires booking % to be COMPLETED (was %)',
            NEW.booking_id, v_booking_status
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_review_requires_completed_booking
    BEFORE INSERT ON review
    FOR EACH ROW EXECUTE FUNCTION enforce_review_requires_completed_booking();
