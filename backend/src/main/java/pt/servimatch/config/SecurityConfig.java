package pt.servimatch.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import pt.servimatch.platform.idempotency.IdempotencyFilter;
import pt.servimatch.platform.idempotency.IdempotencyProperties;
import pt.servimatch.platform.idempotency.IdempotencyStore;
import pt.servimatch.platform.observability.CorrelationIdFilter;
import pt.servimatch.platform.ratelimit.BucketResolver;
import pt.servimatch.platform.ratelimit.RateLimitFilter;
import pt.servimatch.platform.ratelimit.RateLimitProperties;
import pt.servimatch.platform.security.AudienceValidator;
import pt.servimatch.platform.security.KeycloakRoleConverter;
import pt.servimatch.platform.security.ProblemDetailsAccessDeniedHandler;
import pt.servimatch.platform.security.ProblemDetailsAuthenticationEntryPoint;
import pt.servimatch.platform.security.SecurityProperties;
import pt.servimatch.platform.security.UserProvisioningFilter;
import pt.servimatch.platform.security.UserProvisioningPort;

/**
 * Configuração de segurança do backend: <b>apenas</b> OAuth2 Resource
 * Server (ADR-0002/0009). Sem emissão de tokens, sem hash de passwords, sem
 * gestão de sessão de autenticação — é tudo Keycloak.
 *
 * <p>Por omissão, tudo exige autenticação; as únicas exceções são as
 * operações marcadas {@code security: []} em {@code docs/api/openapi.yaml}
 * (contrato congelado v1.0.0) e os endpoints de saúde do Actuator
 * necessários à orquestração. Qualquer novo endpoint público tem de
 * refletir uma alteração equivalente no contrato — não o contrário.
 *
 * <p>Cadeia de filtros transversal (ordem, do mais cedo ao mais tarde):
 * {@link CorrelationIdFilter} → {@link RateLimitFilter} → autenticação
 * Bearer/JWT → {@link UserProvisioningFilter} (ADR-0012 D9, provisionamento
 * JIT de {@code users} a partir do {@code sub} do token) → autorização por
 * role → {@link IdempotencyFilter} (depois da autorização, para poder
 * isolar a chave por principal autenticado).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@EnableConfigurationProperties({SecurityProperties.class, RateLimitProperties.class, IdempotencyProperties.class})
public class SecurityConfig {

    /**
     * Endpoints públicos, alinhados com {@code security: []} no contrato
     * (v1.0.0): estado de versão da app, catálogo de categorias, pesquisa de
     * prestadores, listagem de planos, perfil público do prestador e as
     * suas avaliações (páginas indexáveis {@code /prestadores/{id}}).
     *
     * <p><b>Padrões de um único segmento</b> ({@code /v1/providers/*}) casam
     * também com {@code /v1/providers/me} — por isso
     * {@link #AUTHENTICATED_BEFORE_PUBLIC_GET_ENDPOINTS} tem de ser avaliado
     * <b>antes</b> desta lista na cadeia de {@code authorizeHttpRequests}
     * (a primeira regra que casa decide; ver
     * {@link #securityFilterChain}). Sem essa ordem, um pedido {@code GET
     * /v1/providers/me} sem token seria servido como perfil público em vez
     * de {@code 401}.
     */
    private static final String[] PUBLIC_GET_ENDPOINTS = {
            "/v1/app/version-status",
            "/v1/categories",
            "/v1/search/providers",
            "/v1/subscription-plans",
            "/v1/providers/*",
            "/v1/providers/*/reviews"
    };

    /**
     * Caminhos que, apesar de casarem com um padrão de
     * {@link #PUBLIC_GET_ENDPOINTS}, exigem autenticação — avaliados antes
     * dessa lista na cadeia de autorização. {@code /v1/providers/me} exige
     * role {@code PROVIDER}, imposta por {@code @PreAuthorize} no
     * controlador do módulo {@code providers} (mesmo padrão dos restantes
     * endpoints com role — ver {@code ProposalsController}/
     * {@code RequestsController}); aqui só se garante que fica
     * autenticado, nunca público. {@code /v1/providers/me/requests} já não
     * colidiria com {@code /v1/providers/*} (dois segmentos após
     * {@code /v1/providers/}), mas é listado de forma explícita — a
     * segurança por "o padrão não alcança" é frágil a um padrão futuro mais
     * abrangente.
     */
    private static final String[] AUTHENTICATED_BEFORE_PUBLIC_GET_ENDPOINTS = {
            "/v1/providers/me",
            "/v1/providers/me/requests"
    };

    @Bean
    public JwtDecoder jwtDecoder(SecurityProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri()).build();
        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(properties.issuerUri());
        OAuth2TokenValidator<Jwt> withAudience = new AudienceValidator(properties.expectedAudience());
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(withIssuer, withAudience));
        return decoder;
    }

    @Bean
    public Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter(SecurityProperties properties) {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRoleConverter(properties.rolesClaim()));
        return converter;
    }

    @Bean
    public ProblemDetailsAuthenticationEntryPoint authenticationEntryPoint(ObjectMapper objectMapper) {
        return new ProblemDetailsAuthenticationEntryPoint(objectMapper);
    }

    @Bean
    public ProblemDetailsAccessDeniedHandler accessDeniedHandler(ObjectMapper objectMapper) {
        return new ProblemDetailsAccessDeniedHandler(objectMapper);
    }

    @Bean
    public CorrelationIdFilter correlationIdFilter() {
        return new CorrelationIdFilter();
    }

    @Bean
    public RateLimitFilter rateLimitFilter(BucketResolver bucketResolver, RateLimitProperties properties, ObjectMapper objectMapper) {
        return new RateLimitFilter(bucketResolver, properties, objectMapper);
    }

    @Bean
    public IdempotencyFilter idempotencyFilter(IdempotencyStore idempotencyStore, IdempotencyProperties properties, ObjectMapper objectMapper) {
        return new IdempotencyFilter(idempotencyStore, properties, objectMapper);
    }

    /**
     * {@link ObjectProvider} em vez de injeção direta de
     * {@link UserProvisioningPort}: ver javadoc de {@link UserProvisioningFilter}
     * — o módulo {@code users} pode ainda não ter registado a implementação
     * (ex. contexto de teste de fatia), e este bean não pode falhar a
     * arrancar por isso.
     */
    @Bean
    public UserProvisioningFilter userProvisioningFilter(ObjectProvider<UserProvisioningPort> provisioningPort) {
        return new UserProvisioningFilter(provisioningPort);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter,
            JwtDecoder jwtDecoder,
            ProblemDetailsAuthenticationEntryPoint authenticationEntryPoint,
            ProblemDetailsAccessDeniedHandler accessDeniedHandler,
            CorrelationIdFilter correlationIdFilter,
            RateLimitFilter rateLimitFilter,
            UserProvisioningFilter userProvisioningFilter,
            IdempotencyFilter idempotencyFilter) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        // Ordem deliberada: os caminhos que exigem autenticação apesar de
                        // colidirem com um padrão público (ver javadoc dos dois arrays)
                        // têm de vir ANTES da lista pública.
                        .requestMatchers(HttpMethod.GET, AUTHENTICATED_BEFORE_PUBLIC_GET_ENDPOINTS).authenticated()
                        .requestMatchers(HttpMethod.GET, PUBLIC_GET_ENDPOINTS).permitAll()
                        .requestMatchers(HttpMethod.POST, "/v1/webhooks/payments/**").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        // Qualquer outro endpoint do Actuator (ex. /actuator/prometheus,
                        // exposto em application.yml para scraping) fica atrás de
                        // ROLE_ADMIN — nunca "qualquer autenticado". Sem esta regra
                        // explícita, cairia no anyRequest().authenticated() abaixo e
                        // ficaria legível por qualquer conta CUSTOMER/PROVIDER criada
                        // pelo registo público (ADR-0012): métricas de infraestrutura
                        // não são um dado que um cliente da plataforma deva conseguir ler.
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(rateLimitFilter, BearerTokenAuthenticationFilter.class)
                .addFilterBefore(correlationIdFilter, RateLimitFilter.class)
                .addFilterAfter(userProvisioningFilter, BearerTokenAuthenticationFilter.class)
                .addFilterAfter(idempotencyFilter, AuthorizationFilter.class);
        return http.build();
    }
}
