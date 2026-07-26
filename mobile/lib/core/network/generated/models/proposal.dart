import 'package:freezed_annotation/freezed_annotation.dart';

import 'money.dart';
import 'proposal_status.dart';
import 'provider_summary.dart';

part 'proposal.freezed.dart';
part 'proposal.g.dart';

/// Espelha o schema `Proposal` de docs/api/openapi.yaml.
@freezed
class Proposal with _$Proposal {
  const factory Proposal({
    required String id,
    required String requestId,
    required String providerId,
    ProviderSummary? providerSummary,
    required Money price,
    String? description,
    int? leadTimeDays,
    DateTime? validUntil,
    @JsonKey(unknownEnumValue: ProposalStatus.unknown)
    required ProposalStatus status,
    required DateTime createdAt,
  }) = _Proposal;

  factory Proposal.fromJson(Map<String, dynamic> json) =>
      _$ProposalFromJson(json);
}
