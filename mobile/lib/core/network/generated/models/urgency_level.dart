import 'package:json_annotation/json_annotation.dart';

/// Espelha o schema `UrgencyLevel` de docs/api/openapi.yaml.
enum UrgencyLevel {
  @JsonValue('LOW')
  low,
  @JsonValue('NORMAL')
  normal,
  @JsonValue('HIGH')
  high,
  @JsonValue('URGENT')
  urgent,
}
