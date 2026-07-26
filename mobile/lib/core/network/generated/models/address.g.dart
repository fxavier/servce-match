// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'address.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

_$AddressImpl _$$AddressImplFromJson(Map<String, dynamic> json) =>
    _$AddressImpl(
      line1: json['line1'] as String?,
      line2: json['line2'] as String?,
      postalCode: json['postalCode'] as String?,
      city: json['city'] as String?,
      regionCode: json['regionCode'] as String?,
      country: json['country'] as String?,
      location:
          json['location'] == null
              ? null
              : GeoPoint.fromJson(json['location'] as Map<String, dynamic>),
    );

Map<String, dynamic> _$$AddressImplToJson(_$AddressImpl instance) =>
    <String, dynamic>{
      'line1': instance.line1,
      'line2': instance.line2,
      'postalCode': instance.postalCode,
      'city': instance.city,
      'regionCode': instance.regionCode,
      'country': instance.country,
      'location': instance.location,
    };
