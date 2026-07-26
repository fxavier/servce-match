package pt.servimatch.modules.payments.web.dto;

public record PaymentReferenceDto(String entity, String reference, MoneyDto amount) {
}
