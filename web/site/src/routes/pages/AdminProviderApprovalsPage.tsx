import { Seo } from '../../components/Seo';
import { AdminApprovalConsole } from '../../features/admin/AdminApprovalConsole';

/**
 * Área `/admin` (CLAUDE.md, ARQUITETURA §4.1/§19.1) — defeito C1
 * (`docs/ESTADO-DO-SISTEMA.md`): sem esta página, `approval_status` fica
 * `PENDING` para sempre e `GET /v1/search/providers` devolve vazio em
 * produção. Protegida por `ProtectedRoute roles={['ADMIN']}` — mas essa
 * proteção só esconde a UI de quem não tem o `role`; a autoridade real é o
 * `@PreAuthorize("hasRole('ADMIN')")` no backend (ver
 * `routes/ProtectedRoute.tsx`).
 */
export function AdminProviderApprovalsPage() {
  return (
    <div className="mx-auto max-w-4xl px-5 py-10 sm:px-8 lg:px-10">
      <Seo title="Aprovação de prestadores" description="Decide a aprovação de prestadores." canonicalPath="/admin" />
      <p className="font-mono text-eyebrow tracking-[0.08em] text-muted">ADMINISTRAÇÃO</p>
      <h1 className="mt-1 text-h2 font-display font-bold text-foreground">Aprovação de prestadores</h1>
      <p className="mt-3 max-w-2xl text-body text-muted">
        Regista a decisão sobre a elegibilidade de operação de um prestador. Transições válidas: Pendente → Aprovado
        ou Rejeitado; Aprovado → Suspenso. Qualquer outra transição é recusada pelo servidor (409) — esta consola não
        decide por si, só transporta a tua decisão.
      </p>
      <p className="mt-2 max-w-2xl text-caption text-muted">
        O contrato ainda não tem um endpoint para listar prestadores pendentes de aprovação nem para consultar o
        detalhe de um prestador que não esteja publicamente visível — foi pedido ao <code>api-contract</code>. Até lá,
        a decisão é tomada por identificador de prestador conhecido.
      </p>

      <div className="mt-8">
        <AdminApprovalConsole />
      </div>
    </div>
  );
}
