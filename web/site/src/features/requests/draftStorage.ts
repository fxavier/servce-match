import { EMPTY_DRAFT, wizardDraftSchema, type WizardDraft } from './wizardSchemas';

const KEY = 'sm-request-draft';

/**
 * `sessionStorage` — deliberado, não `localStorage`. A especificação
 * restringe `localStorage` à preferência de tema "e nada mais"; o
 * CLAUDE.md só proíbe **tokens** em `localStorage`/`sessionStorage`, nunca
 * dados de formulário. Precisamos de sobreviver a uma navegação de topo a
 * sério: no modo real, `login()` faz `window.location.href` para o BFF/
 * Keycloak, o que destrói toda a memória React — só armazenamento do
 * browser (não React state) atravessa isso. `sessionStorage` (não
 * `localStorage`) porque o rascunho só faz sentido durante esta visita —
 * não deve sobreviver ao fecho do separador nem acumular entre sessões.
 */
export function saveDraft(draft: WizardDraft): void {
  try {
    sessionStorage.setItem(KEY, JSON.stringify(draft));
  } catch {
    // Modo privado ou quota excedida — perde-se o auto-save, não é fatal.
  }
}

export function loadDraft(): WizardDraft | undefined {
  try {
    const raw = sessionStorage.getItem(KEY);
    if (!raw) return undefined;
    const parsed = wizardDraftSchema.safeParse(JSON.parse(raw));
    return parsed.success ? parsed.data : undefined;
  } catch {
    return undefined;
  }
}

export function clearDraft(): void {
  try {
    sessionStorage.removeItem(KEY);
  } catch {
    // nada a fazer
  }
}

export { EMPTY_DRAFT };
