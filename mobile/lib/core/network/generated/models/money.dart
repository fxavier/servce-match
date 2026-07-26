import 'package:freezed_annotation/freezed_annotation.dart';

part 'money.freezed.dart';
part 'money.g.dart';

/// Espelha o schema `Money` de docs/api/openapi.yaml.
///
/// Dinheiro é sempre inteiro na menor unidade + ISO-4217 (CLAUDE.md §5).
/// Nunca usar `double` para representar montantes.
@freezed
class Money with _$Money {
  const factory Money({
    required int amountCents,
    required String currency,
  }) = _Money;

  factory Money.fromJson(Map<String, dynamic> json) => _$MoneyFromJson(json);
}
