import { describe, expect, it } from 'vitest';
import { isProblemDetails, isSubscriptionRequired } from './problem';

describe('isProblemDetails', () => {
  it('aceita um objeto com status e title', () => {
    expect(isProblemDetails({ status: 422, title: 'Dados inválidos' })).toBe(true);
  });

  it('rejeita valores sem a forma RFC 9457', () => {
    expect(isProblemDetails(new Error('boom'))).toBe(false);
    expect(isProblemDetails(undefined)).toBe(false);
    expect(isProblemDetails({ message: 'oops' })).toBe(false);
  });
});

describe('isSubscriptionRequired', () => {
  it('ramifica pelo type, nunca pelo texto', () => {
    expect(
      isSubscriptionRequired({
        type: 'https://errors.servimatch.pt/subscription-required',
        title: 'texto que pode mudar a qualquer momento',
        status: 403,
      }),
    ).toBe(true);
    expect(
      isSubscriptionRequired({
        type: 'https://errors.servimatch.pt/validation',
        title: 'subscription required in this text but not in type',
        status: 422,
      }),
    ).toBe(false);
  });
});
