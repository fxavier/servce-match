package pt.servimatch.platform.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * Provisionamento <em>just-in-time</em> de utilizadores (ADR-0012 D9): para
 * cada pedido autenticado por Bearer JWT, garante que existe uma linha
 * {@code users} para o {@code sub} do token, delegando em
 * {@link UserProvisioningPort}. Registado depois da autenticação OAuth2 na
 * cadeia de segurança ({@code pt.servimatch.config.SecurityConfig}) — só
 * corre quando o {@link SecurityContextHolder} já tem uma
 * {@link JwtAuthenticationToken} resolvida.
 *
 * <p><b>{@link ObjectProvider}, nunca um bean no-op com
 * {@code @ConditionalOnMissingBean}.</b> A ordem de registo de beans de
 * aplicação (fora de auto-configuração, que é o único sítio onde
 * {@code @ConditionalOnMissingBean} tem garantia formal de correr por
 * último) não é determinística — um bean no-op registado antes da
 * implementação real do módulo {@code users} "ganharia" a condição em
 * silêncio, e o provisionamento deixaria de acontecer sem qualquer erro.
 * {@link ObjectProvider#getIfAvailable()} evita esse risco: sem nenhum bean
 * {@link UserProvisioningPort} no contexto, o filtro não faz nada — nunca
 * escolhe um substituto errado.
 *
 * <p><b>Cache local (Caffeine) dos {@code sub} já provisionados.</b> Sem
 * ela, este filtro forçaria uma escrita (ou, no mínimo, uma consulta) à
 * tabela {@code users} em <em>todos</em> os pedidos autenticados — o oposto
 * do que a nota de implementação pede ("não faças um SELECT por pedido").
 * Uma entrada só é adicionada à cache **depois** de
 * {@link UserProvisioningPort#ensureProvisioned} ter tido sucesso; nunca
 * antes e nunca em caso de falha — o único efeito de uma entrada não estar
 * em cache é uma chamada extra e idempotente, nunca uma omissão. A cache é
 * por processo (não partilhada via Redis mesmo em multi-instância, ADR-0006):
 * a pior consequência de uma cache fria numa instância nova é uma chamada
 * extra por instância, não uma incorreção — não vale a infraestrutura
 * partilhada para uma otimização, ao contrário do <em>rate limiting</em> e
 * da idempotência, onde a correção depende de um estado partilhado.
 *
 * <p><b>Falhas nunca derrubam o pedido, e nunca vão a log com PII.</b> Um
 * filtro de servlet corre fora do {@code DispatcherServlet}, logo escapa ao
 * {@code pt.servimatch.platform.error.GlobalExceptionHandler}: uma exceção
 * não apanhada aqui devolveria um erro genérico do contentor em vez de um
 * corpo RFC 9457. O log de falha regista só o {@code sub} (identificador
 * opaco do IdP, não PII) — nunca o email nem o nome (CLAUDE.md §4).
 */
public class UserProvisioningFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(UserProvisioningFilter.class);

    private final ObjectProvider<UserProvisioningPort> provisioningPort;

    private final Cache<String, Boolean> provisionedSubs = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofHours(6))
            .maximumSize(100_000)
            .build();

    public UserProvisioningFilter(ObjectProvider<UserProvisioningPort> provisioningPort) {
        this.provisioningPort = provisioningPort;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            provisionIfNeeded(jwtAuthentication.getToken());
        }
        filterChain.doFilter(request, response);
    }

    private void provisionIfNeeded(Jwt jwt) {
        String sub = jwt.getSubject();
        if (sub == null || sub.isBlank() || provisionedSubs.getIfPresent(sub) != null) {
            return;
        }
        UserProvisioningPort port = provisioningPort.getIfAvailable();
        if (port == null) {
            // Módulo `users` ainda não publicou a implementação neste contexto
            // (ex.: arranque parcial em teste de fatia). Nada a fazer — sem
            // substituto, sem falha do pedido.
            return;
        }
        try {
            String email = firstNonBlank(jwt.getClaimAsString("email"), jwt.getClaimAsString("preferred_username"), sub);
            String displayName = firstNonBlank(jwt.getClaimAsString("name"), jwt.getClaimAsString("preferred_username"), email);
            port.ensureProvisioned(sub, email, displayName);
            provisionedSubs.put(sub, Boolean.TRUE);
        } catch (RuntimeException e) {
            log.warn("Provisionamento JIT falhou para sub={} — pedido continua sem a garantia de linha `users`.", sub, e);
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
