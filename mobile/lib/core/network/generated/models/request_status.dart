import 'package:json_annotation/json_annotation.dart';

/// Espelha o schema `RequestStatus` de docs/api/openapi.yaml.
///
/// Inclui [unknown] como rede de segurança: o servidor evolui de forma
/// aditiva (novos valores de enum) e uma app instalada há meses não pode
/// rebentar o parse por causa de um valor que ainda não conhece.
enum RequestStatus {
  @JsonValue('DRAFT')
  draft,
  @JsonValue('PUBLISHED')
  published,
  @JsonValue('IN_NEGOTIATION')
  inNegotiation,
  @JsonValue('CONFIRMED')
  confirmed,
  @JsonValue('IN_PROGRESS')
  inProgress,
  @JsonValue('COMPLETED')
  completed,
  @JsonValue('CANCELLED')
  cancelled,
  @JsonValue('__unknown__')
  unknown,
}
