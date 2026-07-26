package pt.servimatch.modules.billing.web.dto;

/** Espelha o schema {@code Money} do contrato: inteiro + ISO-4217, nunca {@code double}. */
public record MoneyDto(long amountCents, String currency) {
}
