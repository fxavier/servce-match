import 'package:json_annotation/json_annotation.dart';

/// Espelha o schema `ProposalStatus` de docs/api/openapi.yaml.
/// Ver nota sobre [unknown] em `request_status.dart`.
enum ProposalStatus {
  @JsonValue('SENT')
  sent,
  @JsonValue('ACCEPTED')
  accepted,
  @JsonValue('REJECTED')
  rejected,
  @JsonValue('CANCELLED')
  cancelled,
  @JsonValue('EXPIRED')
  expired,
  @JsonValue('SUPERSEDED')
  superseded,
  @JsonValue('__unknown__')
  unknown,
}
