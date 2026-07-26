// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'version_status.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

_$VersionStatusImpl _$$VersionStatusImplFromJson(Map<String, dynamic> json) =>
    _$VersionStatusImpl(
      status: $enumDecode(
        _$AppUpdateStateEnumMap,
        json['status'],
        unknownValue: AppUpdateState.unknown,
      ),
      minSupportedVersion: json['minSupportedVersion'] as String?,
      latestVersion: json['latestVersion'] as String?,
      storeUrl: json['storeUrl'] as String?,
      message: json['message'] as String?,
    );

Map<String, dynamic> _$$VersionStatusImplToJson(_$VersionStatusImpl instance) =>
    <String, dynamic>{
      'status': _$AppUpdateStateEnumMap[instance.status]!,
      'minSupportedVersion': instance.minSupportedVersion,
      'latestVersion': instance.latestVersion,
      'storeUrl': instance.storeUrl,
      'message': instance.message,
    };

const _$AppUpdateStateEnumMap = {
  AppUpdateState.ok: 'OK',
  AppUpdateState.updateRecommended: 'UPDATE_RECOMMENDED',
  AppUpdateState.updateRequired: 'UPDATE_REQUIRED',
  AppUpdateState.unknown: '__unknown__',
};
