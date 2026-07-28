import { useCategories } from '../../categories/useCategories';
import type { WizardDraft } from '../wizardSchemas';

const URGENCY_LABEL: Record<string, string> = { LOW: 'Sem pressa', NORMAL: 'Normal', HIGH: 'Prioritário', URGENT: 'Urgente' };

export function ReviewPanel({ draft, photoCount }: { draft: WizardDraft; photoCount: number }) {
  const { data: categories } = useCategories();
  const category = categories?.find((candidate) => candidate.id === draft.categoryId);

  return (
    <aside className="surface-card sticky top-24 hidden h-fit w-full max-w-xs flex-col gap-4 p-5 lg:flex">
      <p className="eyebrow text-muted">RESUMO DO PEDIDO</p>
      <dl className="flex flex-col gap-3 text-body">
        <div>
          <dt className="text-caption text-muted">Categoria</dt>
          <dd className="font-medium text-foreground">{category?.name ?? '—'}</dd>
        </div>
        <div>
          <dt className="text-caption text-muted">Título</dt>
          <dd className="font-medium text-foreground">{draft.title || '—'}</dd>
        </div>
        <div>
          <dt className="text-caption text-muted">Urgência</dt>
          <dd className="font-medium text-foreground">{URGENCY_LABEL[draft.urgency]}</dd>
        </div>
        <div>
          <dt className="text-caption text-muted">Morada</dt>
          <dd className="font-medium text-foreground">
            {draft.line1 ? `${draft.line1}, ${draft.postalCode} ${draft.city}` : '—'}
          </dd>
        </div>
        <div>
          <dt className="text-caption text-muted">Fotografias</dt>
          <dd className="font-medium text-foreground">{photoCount > 0 ? `${photoCount} anexada(s)` : 'Nenhuma'}</dd>
        </div>
      </dl>
    </aside>
  );
}
