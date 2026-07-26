import 'package:freezed_annotation/freezed_annotation.dart';

part 'provider_summary.freezed.dart';
part 'provider_summary.g.dart';

/// Espelha o schema `ProviderSummary` de docs/api/openapi.yaml.
@freezed
class ProviderSummary with _$ProviderSummary {
  const factory ProviderSummary({
    required String id,
    required String displayName,
    String? headline,
    String? companyName,
    required double ratingAvg,
    required int ratingCount,
    bool? verified,
    bool? premiumBadge,
    String? avatarUrl,
  }) = _ProviderSummary;

  factory ProviderSummary.fromJson(Map<String, dynamic> json) =>
      _$ProviderSummaryFromJson(json);
}
