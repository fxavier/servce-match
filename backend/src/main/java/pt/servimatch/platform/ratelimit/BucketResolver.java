package pt.servimatch.platform.ratelimit;

import io.github.bucket4j.Bucket;

import java.time.Duration;

/**
 * Obtém (ou cria) o {@link Bucket} associado a uma chave de rate limiting,
 * com capacidade e período de refil "greedy" (distribuído ao longo da
 * janela, em vez de um único refil no fim do período).
 *
 * <p>Implementações: em memória (single-instance) ou Redis (multi-instância,
 * ADR-0006) — a escolha é por configuração
 * ({@code servimatch.rate-limit.backend}), não por ramificação no código
 * que consome esta interface.
 */
public interface BucketResolver {

    Bucket resolve(String key, long capacity, Duration period);
}
