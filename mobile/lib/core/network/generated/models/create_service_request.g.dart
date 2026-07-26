// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'create_service_request.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

_$CreateServiceRequestImpl _$$CreateServiceRequestImplFromJson(
  Map<String, dynamic> json,
) => _$CreateServiceRequestImpl(
  categoryId: json['categoryId'] as String,
  title: json['title'] as String,
  description: json['description'] as String?,
  address: Address.fromJson(json['address'] as Map<String, dynamic>),
  urgency: $enumDecodeNullable(_$UrgencyLevelEnumMap, json['urgency']),
  availability: json['availability'] as String?,
  imageIds:
      (json['imageIds'] as List<dynamic>?)?.map((e) => e as String).toList() ??
      const <String>[],
);

Map<String, dynamic> _$$CreateServiceRequestImplToJson(
  _$CreateServiceRequestImpl instance,
) => <String, dynamic>{
  'categoryId': instance.categoryId,
  'title': instance.title,
  'description': instance.description,
  'address': instance.address,
  'urgency': _$UrgencyLevelEnumMap[instance.urgency],
  'availability': instance.availability,
  'imageIds': instance.imageIds,
};

const _$UrgencyLevelEnumMap = {
  UrgencyLevel.low: 'LOW',
  UrgencyLevel.normal: 'NORMAL',
  UrgencyLevel.high: 'HIGH',
  UrgencyLevel.urgent: 'URGENT',
};
