/**
 * Requests — pedidos de serviço do cliente e a respetiva máquina de
 * estados ({@code DRAFT → PUBLISHED → IN_NEGOTIATION → CONFIRMED →
 * IN_PROGRESS → COMPLETED}, com {@code CANCELLED} como saída lateral).
 * Publica {@code RequestPublished} para acionar o matching assíncrono.
 *
 * <p>Fronteira declarada pelo agente {@code backend-platform} na Onda 0
 * (esqueleto vazio). Implementação pertence ao agente {@code backend-domain}
 * — ver {@code docs/AGENTES.md} e {@code docs/ARQUITETURA.md} §4.3, §6.3.
 *
 * <p>{@code allowedDependencies} fechado nesta revisão (Onda 1c,
 * {@code backend-platform}), a partir dos imports reais de
 * {@code internal.RequestsController}/{@code internal.RequestsService}:
 * <ul>
 *   <li>{@code users} — identidade do autor do pedido.</li>
 *   <li>{@code uploads} — anexos do pedido via {@code UploadsApi}, já sem
 *       leitura direta a {@code upload_asset}.</li>
 *   <li>{@code categories} — catálogo via {@code CategoriesApi}, já sem
 *       leitura direta a {@code category} (substitui o
 *       {@code CategoryLookup} temporário mencionado em
 *       {@code categories.package-info}).</li>
 *   <li>{@code providers} — {@code ProvidersApi} para o gating por
 *       subscrição no inbox do prestador e no detalhe do pedido
 *       (commit {@code fix(requests): close provider inbox and
 *       request-detail data leak via matching eligibility}).</li>
 *   <li>{@code matching} — {@code MatchingApi#isEligible}/
 *       {@code #filterEligibleRequestIds} para o mesmo gating.</li>
 *   <li>{@code geo} — <strong>achado, não decisão deliberada</strong>:
 *       {@code RequestsService} constrói um {@code geo.GeoPoint} porque
 *       {@code matching.EligibilityQuery} o exige no construtor. O
 *       {@code geo.package-info} documenta o módulo como "consumido por
 *       {@code matching} e {@code search}, nunca o inverso" — esta
 *       dependência direta de {@code requests} contradiz essa intenção. A
 *       causa é a assinatura pública de {@code EligibilityQuery}, que
 *       expõe o tipo {@code geo.GeoPoint} em vez de coordenadas primitivas;
 *       corrigir isso é alterar API do {@code matching}
 *       ({@code backend-matching}), fora deste âmbito. Declarada aqui para
 *       o {@code verify()} refletir a realidade; assinalada para o
 *       {@code arquiteto}/{@code backend-matching} decidirem se vale a pena
 *       remover o vazamento de tipo.</li>
 * </ul>
 *
 * <p>Inalterado na revisão "dados-reais" (Onda 1): {@code GET /v1/requests}
 * (listar os meus pedidos, {@code role CUSTOMER}, ainda por implementar)
 * não precisa de nenhuma dependência nova — filtra {@code service_request}
 * pelo {@code customerId} autenticado, já resolvido por {@code UsersApi}.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Requests",
        allowedDependencies = {
                "modules.users", "modules.uploads", "modules.categories",
                "modules.providers", "modules.matching", "modules.geo"
        }
)
package pt.servimatch.modules.requests;
