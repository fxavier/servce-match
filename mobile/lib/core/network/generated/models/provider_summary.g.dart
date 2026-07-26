// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'provider_summary.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

_$ProviderSummaryImpl _$$ProviderSummaryImplFromJson(
  Map<String, dynamic> json,
) => _$ProviderSummaryImpl(
  id: json['id'] as String,
  displayName: json['displayName'] as String,
  headline: json['headline'] as String?,
  companyName: json['companyName'] as String?,
  ratingAvg: (json['ratingAvg'] as num).toDouble(),
  ratingCount: (json['ratingCount'] as num).toInt(),
  verified: json['verified'] as bool?,
  premiumBadge: json['premiumBadge'] as bool?,
  avatarUrl: json['avatarUrl'] as String?,
);

Map<String, dynamic> _$$ProviderSummaryImplToJson(
  _$ProviderSummaryImpl instance,
) => <String, dynamic>{
  'id': instance.id,
  'displayName': instance.displayName,
  'headline': instance.headline,
  'companyName': instance.companyName,
  'ratingAvg': instance.ratingAvg,
  'ratingCount': instance.ratingCount,
  'verified': instance.verified,
  'premiumBadge': instance.premiumBadge,
  'avatarUrl': instance.avatarUrl,
};
