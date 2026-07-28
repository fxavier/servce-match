import { describe, expect, it } from 'vitest';
import { fieldErrorsFrom, isProblemDetails, isSubscriptionRequired, isValidationProblem, toProblem } from './problem';

describe('isProblemDetails', () => {
  it('reconhece um Problem Details válido', () => {
    expect(isProblemDetails({ title: 'Erro', status: 422 })).toBe(true);
  });

  it('rejeita valores sem title/status', () => {
    expect(isProblemDetails({ message: 'oops' })).toBe(false);
    expect(isProblemDetails(null)).toBe(false);
    expect(isProblemDetails(undefined)).toBe(false);
    expect(isProblemDetails('erro')).toBe(false);
  });
});

describe('isSubscriptionRequired — ramifica pelo type, nunca por texto', () => {
  it('identifica o 403 de subscrição por type', () => {
    expect(
      isSubscriptionRequired({
        type: 'https://errors.servimatch.pt/subscription-required',
        title: 'Subscrição necessária',
        status: 403,
      }),
    ).toBe(true);
  });

  it('não confunde outro 403 (mesmo título parecido) com subscription-required', () => {
    expect(
      isSubscriptionRequired({
        type: 'https://errors.servimatch.pt/forbidden',
        title: 'É preciso uma subscrição ativa', // texto parecido de propósito — não deve importar
        status: 403,
      }),
    ).toBe(false);
  });
});

describe('isValidationProblem', () => {
  it('identifica 422 mesmo sem type explícito', () => {
    expect(isValidationProblem({ title: 'Dados inválidos', status: 422 })).toBe(true);
  });
});

describe('toProblem', () => {
  it('devolve o Problem Details tal como veio, se já for um', () => {
    const problem = { type: 'x', title: 'Erro', status: 409 };
    expect(toProblem(problem)).toBe(problem);
  });

  it('normaliza erros de rede/parsing para um Problem Details apresentável', () => {
    const result = toProblem(new TypeError('Failed to fetch'));
    expect(result.status).toBe(0);
    expect(result.title).toBeTruthy();
  });
});

describe('fieldErrorsFrom', () => {
  it('mapeia errors[] de um 422 filtrando entradas incompletas', () => {
    const problem = {
      title: 'Dados inválidos',
      status: 422,
      errors: [
        { field: 'categoryId', message: 'Obrigatório' },
        { field: 'title' }, // sem message — deve ser filtrado
        {},
      ],
    };
    expect(fieldErrorsFrom(problem)).toEqual([{ field: 'categoryId', message: 'Obrigatório' }]);
  });
});
