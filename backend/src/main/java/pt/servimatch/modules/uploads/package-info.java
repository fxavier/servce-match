/**
 * Uploads — emissão de URLs pré-assinados para escrita direta no object
 * storage (o ficheiro nunca atravessa o backend, ARQUITETURA §8.6/§11.2) e
 * confirmação por <em>magic bytes</em> quando um {@code imageId} é
 * associado a um recurso de outro módulo (CLAUDE.md §4).
 *
 * <p>Módulo transversal, propriedade do agente {@code backend-platform}
 * (como {@code notifications}): não tem semântica de domínio própria, só
 * infraestrutura reutilizável por qualquer módulo que aceite anexos
 * ({@code requests}, {@code chat}, {@code providers}, ...) através da API
 * pública {@link pt.servimatch.modules.uploads.UploadsApi} — nunca lendo
 * {@code upload_asset} diretamente.
 *
 * <p>{@code allowedDependencies = {"users"}}: só depende de {@code users}
 * para resolver o {@code sub} do JWT no seu próprio controlador
 * ({@code POST /v1/uploads}); não depende de nenhum módulo de domínio
 * consumidor — a direção da dependência é sempre consumidor → uploads,
 * nunca o inverso.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Uploads",
        allowedDependencies = {"modules.users"}
)
package pt.servimatch.modules.uploads;
