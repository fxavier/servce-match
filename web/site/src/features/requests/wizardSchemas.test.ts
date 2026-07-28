import { describe, expect, it } from 'vitest';
import { stepAddressSchema, stepCategorySchema, stepDetailsSchema } from './wizardSchemas';

describe('stepCategorySchema — validação Zod do passo 1', () => {
  it('rejeita categoria vazia', () => {
    const result = stepCategorySchema.safeParse({ categoryId: '' });
    expect(result.success).toBe(false);
  });

  it('aceita uma categoria escolhida', () => {
    const result = stepCategorySchema.safeParse({ categoryId: 'e659b54f-0c22-4374-ad36-52a1ab62cf74' });
    expect(result.success).toBe(true);
  });
});

describe('stepDetailsSchema — validação Zod do passo 2', () => {
  it('rejeita título demasiado curto', () => {
    const result = stepDetailsSchema.safeParse({ title: 'Ab', urgency: 'NORMAL' });
    expect(result.success).toBe(false);
  });

  it('aceita um título e urgência válidos, descrição/disponibilidade opcionais', () => {
    const result = stepDetailsSchema.safeParse({ title: 'Fuga de água na cozinha', urgency: 'HIGH' });
    expect(result.success).toBe(true);
  });
});

describe('stepAddressSchema — validação Zod do passo 3', () => {
  it('rejeita código postal em formato inválido', () => {
    const result = stepAddressSchema.safeParse({ line1: 'Rua X, 10', postalCode: '1000001', city: 'Lisboa', regionCode: 'PT-LIS' });
    expect(result.success).toBe(false);
  });

  it('aceita morada completa com código postal no formato 0000-000', () => {
    const result = stepAddressSchema.safeParse({ line1: 'Rua X, 10', postalCode: '1000-001', city: 'Lisboa', regionCode: 'PT-LIS' });
    expect(result.success).toBe(true);
  });
});
