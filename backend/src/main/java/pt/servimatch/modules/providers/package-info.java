/**
 * Providers — perfil profissional/empresa, portfólio, categorias e zonas
 * de atuação do prestador; elegibilidade de operação resolvida na leitura
 * (ADR-0011), nunca a partir de estado projetado.
 *
 * <p>Fronteira declarada pelo agente {@code backend-platform}. Implementação
 * pertence ao agente {@code backend-domain} — ver {@code docs/AGENTES.md} e
 * {@code docs/ARQUITETURA.md} §6.3, §9, e ADR-0011 (elegibilidade do
 * prestador resolvida na leitura).
 *
 * <p>{@code allowedDependencies} (revisão "dados-reais", Onda 1,
 * {@code backend-platform}):
 * <ul>
 *   <li>{@code users} — {@code UsersApi}, para o {@code sub} do JWT e para
 *       resolver {@code displayName} em {@code ProvidersApi.summary}.</li>
 *   <li>{@code billing} — <b>nova nesta revisão, ADR-0011 D2/D3/D8.</b> O
 *       facto "subscrição concede visibilidade" só é respondido por
 *       {@code billing.SubscriptionLifecycle.isVisibilityEligible}; este
 *       módulo compõe-o com o facto "aprovação"
 *       ({@code provider_profile.approval_status}, que só {@code providers}
 *       responde) em {@code ProvidersApi.checkEligibility}
 *       — {@code record ProviderEligibility(approved, visible)}. Fecha o
 *       defeito documentado no ADR: {@code checkEligibility} lia
 *       {@code visibility_state}, uma coluna sem escritor em produção, e
 *       nunca consultava a subscrição. Também usada para {@code premiumBadge}
 *       ({@code SubscriptionLifecycle.findByProvider}/{@code findPlan}),
 *       fechando a divergência registada como achado no ADR-0011 D9 (antes
 *       fixo a {@code false}). Direção sem ciclo: {@code billing} mantém
 *       {@code allowedDependencies = {}} (ver o seu {@code package-info}) —
 *       nunca {@code billing → providers}.</li>
 *   <li>{@code categories} — <b>nova nesta revisão.</b>
 *       {@code CategoriesApi}, para validar {@code categoryIds} em
 *       {@code PUT /v1/providers/me} (rejeita categoria desconhecida antes
 *       de qualquer escrita) e para resolver nomes de categoria no perfil
 *       público/próprio. Módulo-folha, sem risco de ciclo.</li>
 *   <li>{@code uploads} — <b>nova nesta revisão.</b> {@code UploadsApi},
 *       para confirmar posse + <em>purpose</em>
 *       ({@code PROVIDER_PORTFOLIO}) das imagens de portefólio em
 *       {@code PUT /v1/providers/me} ({@code confirmOwnedUpload}, que inclui
 *       a validação por <em>magic bytes</em>, CLAUDE.md §4) e para resolver
 *       {@code image_asset_id} → URL assinada de leitura
 *       ({@code UploadsApi#resolve}) no perfil — nunca lendo
 *       {@code upload_asset} diretamente (ADR-0010). {@code uploads} não
 *       depende de {@code providers} (só de {@code users}), sem ciclo.</li>
 * </ul>
 *
 * <p><b>Nunca {@code providers → reviews}.</b> {@code reviews} já depende de
 * {@code providers} (ver {@code reviews.package-info}, para o endpoint
 * público {@code GET /v1/providers/{id}/reviews}); a direção inversa
 * fecharia um ciclo que {@code ApplicationModules.verify()} rejeita. Se um
 * dia {@code providers} precisar de algo de {@code reviews} (ex.: agregar
 * {@code rating_avg}/{@code rating_count} — achado ainda não resolvido do
 * ADR-0011 D9), a via é evento de domínio ou leitura SQL sob ADR-0010, não
 * uma dependência Java direta; escalar ao {@code arquiteto} antes de a
 * abrir aqui.
 *
 * <p><b>{@code platform.audit} — nenhuma alteração a {@code allowedDependencies}
 * (decisão desta revisão, defeito C1 de {@code docs/ESTADO-DO-SISTEMA.md}).</b>
 * A decisão administrativa de aprovação de prestador
 * ({@code PATCH /v1/admin/providers/{providerId}/approval}, a implementar por
 * {@code backend-domain-providers}) regista-se em {@code audit_log} através de
 * {@code pt.servimatch.platform.audit.AuditLogWriter}. Não é preciso declarar
 * {@code platform} em {@code allowedDependencies} — segue exatamente o mesmo
 * padrão já em uso por {@code platform.error} (ver {@code internal.Problems})
 * e {@code platform.idempotency} em todos os módulos do projeto, nenhum dos
 * quais declara {@code platform}: {@code application.yml} configura
 * {@code spring.modulith.detection-strategy: explicitly-annotated}, pelo que
 * só um pacote com {@code @ApplicationModule} no seu {@code package-info.java}
 * é reconhecido como módulo de aplicação por {@code ApplicationModules.verify()}.
 * {@code pt.servimatch.platform} e os seus subpacotes ({@code audit},
 * {@code error}, {@code idempotency}, {@code observability}, ...) não têm
 * {@code package-info.java} próprio — não são módulos, são infraestrutura
 * partilhada, livremente importável por qualquer módulo. Confirmado por
 * {@code ModularityTests.verifiesModularStructure()} verde com esta chamada
 * em vigor.
 *
 * <p>Um módulo que precise de uma dependência nova pede-a a este agente.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Providers",
        allowedDependencies = {"modules.users", "modules.billing", "modules.categories", "modules.uploads"}
)
package pt.servimatch.modules.providers;
