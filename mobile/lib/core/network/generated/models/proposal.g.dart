// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'proposal.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

_$ProposalImpl _$$ProposalImplFromJson(Map<String, dynamic> json) =>
    _$ProposalImpl(
      id: json['id'] as String,
      requestId: json['requestId'] as String,
      providerId: json['providerId'] as String,
      providerSummary:
          json['providerSummary'] == null
              ? null
              : ProviderSummary.fromJson(
                json['providerSummary'] as Map<String, dynamic>,
              ),
      price: Money.fromJson(json['price'] as Map<String, dynamic>),
      description: json['description'] as String?,
      leadTimeDays: (json['leadTimeDays'] as num?)?.toInt(),
      validUntil:
          json['validUntil'] == null
              ? null
              : DateTime.parse(json['validUntil'] as String),
      status: $enumDecode(
        _$ProposalStatusEnumMap,
        json['status'],
        unknownValue: ProposalStatus.unknown,
      ),
      createdAt: DateTime.parse(json['createdAt'] as String),
    );

Map<String, dynamic> _$$ProposalImplToJson(_$ProposalImpl instance) =>
    <String, dynamic>{
      'id': instance.id,
      'requestId': instance.requestId,
      'providerId': instance.providerId,
      'providerSummary': instance.providerSummary,
      'price': instance.price,
      'description': instance.description,
      'leadTimeDays': instance.leadTimeDays,
      'validUntil': instance.validUntil?.toIso8601String(),
      'status': _$ProposalStatusEnumMap[instance.status]!,
      'createdAt': instance.createdAt.toIso8601String(),
    };

const _$ProposalStatusEnumMap = {
  ProposalStatus.sent: 'SENT',
  ProposalStatus.accepted: 'ACCEPTED',
  ProposalStatus.rejected: 'REJECTED',
  ProposalStatus.cancelled: 'CANCELLED',
  ProposalStatus.expired: 'EXPIRED',
  ProposalStatus.superseded: 'SUPERSEDED',
  ProposalStatus.unknown: '__unknown__',
};
