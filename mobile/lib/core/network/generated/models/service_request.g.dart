// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'service_request.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

_$ServiceRequestImpl _$$ServiceRequestImplFromJson(Map<String, dynamic> json) =>
    _$ServiceRequestImpl(
      id: json['id'] as String,
      customerId: json['customerId'] as String,
      category:
          json['category'] == null
              ? null
              : Category.fromJson(json['category'] as Map<String, dynamic>),
      title: json['title'] as String,
      description: json['description'] as String?,
      address:
          json['address'] == null
              ? null
              : Address.fromJson(json['address'] as Map<String, dynamic>),
      urgency: $enumDecodeNullable(_$UrgencyLevelEnumMap, json['urgency']),
      availability: json['availability'] as String?,
      status: $enumDecode(
        _$RequestStatusEnumMap,
        json['status'],
        unknownValue: RequestStatus.unknown,
      ),
      images:
          (json['images'] as List<dynamic>?)
              ?.map((e) => ImageRef.fromJson(e as Map<String, dynamic>))
              .toList() ??
          const <ImageRef>[],
      proposalCount: (json['proposalCount'] as num?)?.toInt(),
      createdAt: DateTime.parse(json['createdAt'] as String),
      publishedAt:
          json['publishedAt'] == null
              ? null
              : DateTime.parse(json['publishedAt'] as String),
    );

Map<String, dynamic> _$$ServiceRequestImplToJson(
  _$ServiceRequestImpl instance,
) => <String, dynamic>{
  'id': instance.id,
  'customerId': instance.customerId,
  'category': instance.category,
  'title': instance.title,
  'description': instance.description,
  'address': instance.address,
  'urgency': _$UrgencyLevelEnumMap[instance.urgency],
  'availability': instance.availability,
  'status': _$RequestStatusEnumMap[instance.status]!,
  'images': instance.images,
  'proposalCount': instance.proposalCount,
  'createdAt': instance.createdAt.toIso8601String(),
  'publishedAt': instance.publishedAt?.toIso8601String(),
};

const _$UrgencyLevelEnumMap = {
  UrgencyLevel.low: 'LOW',
  UrgencyLevel.normal: 'NORMAL',
  UrgencyLevel.high: 'HIGH',
  UrgencyLevel.urgent: 'URGENT',
};

const _$RequestStatusEnumMap = {
  RequestStatus.draft: 'DRAFT',
  RequestStatus.published: 'PUBLISHED',
  RequestStatus.inNegotiation: 'IN_NEGOTIATION',
  RequestStatus.confirmed: 'CONFIRMED',
  RequestStatus.inProgress: 'IN_PROGRESS',
  RequestStatus.completed: 'COMPLETED',
  RequestStatus.cancelled: 'CANCELLED',
  RequestStatus.unknown: '__unknown__',
};
