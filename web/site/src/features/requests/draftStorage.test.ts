import { beforeEach, describe, expect, it } from 'vitest';
import { clearDraft, EMPTY_DRAFT, loadDraft, saveDraft } from './draftStorage';

describe('draftStorage — preservação do rascunho através do login (§7)', () => {
  beforeEach(() => {
    sessionStorage.clear();
  });

  it('devolve undefined quando não há rascunho guardado', () => {
    expect(loadDraft()).toBeUndefined();
  });

  it('guarda e recupera o rascunho tal como foi gravado', () => {
    const draft = { ...EMPTY_DRAFT, categoryId: 'cat-1', title: 'Fuga de água', pendingPublish: true };
    saveDraft(draft);
    expect(loadDraft()).toEqual(draft);
  });

  it('usa sessionStorage, não localStorage — sobrevive a uma navegação de topo mas não é um token', () => {
    const draft = { ...EMPTY_DRAFT, title: 'Pintura de sala' };
    saveDraft(draft);
    expect(localStorage.getItem('sm-request-draft')).toBeNull();
    expect(sessionStorage.getItem('sm-request-draft')).not.toBeNull();
  });

  it('limpa o rascunho depois de publicar', () => {
    saveDraft({ ...EMPTY_DRAFT, title: 'X' });
    clearDraft();
    expect(loadDraft()).toBeUndefined();
  });
});
