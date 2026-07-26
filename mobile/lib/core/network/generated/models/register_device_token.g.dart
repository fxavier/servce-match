// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'register_device_token.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

_$RegisterDeviceTokenImpl _$$RegisterDeviceTokenImplFromJson(
  Map<String, dynamic> json,
) => _$RegisterDeviceTokenImpl(
  token: json['token'] as String,
  platform: $enumDecode(_$ApiPlatformEnumMap, json['platform']),
  appVersion: json['appVersion'] as String?,
);

Map<String, dynamic> _$$RegisterDeviceTokenImplToJson(
  _$RegisterDeviceTokenImpl instance,
) => <String, dynamic>{
  'token': instance.token,
  'platform': _$ApiPlatformEnumMap[instance.platform]!,
  'appVersion': instance.appVersion,
};

const _$ApiPlatformEnumMap = {
  ApiPlatform.ios: 'IOS',
  ApiPlatform.android: 'ANDROID',
  ApiPlatform.web: 'WEB',
};
