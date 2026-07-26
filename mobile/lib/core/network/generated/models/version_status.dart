import 'package:freezed_annotation/freezed_annotation.dart';

part 'version_status.freezed.dart';
part 'version_status.g.dart';

/// Espelha o schema `VersionStatus.status` de docs/api/openapi.yaml.
enum AppUpdateState {
  @JsonValue('OK')
  ok,
  @JsonValue('UPDATE_RECOMMENDED')
  updateRecommended,
  @JsonValue('UPDATE_REQUIRED')
  updateRequired,
  @JsonValue('__unknown__')
  unknown,
}

/// Espelha o schema `VersionStatus` de `GET /v1/app/version-status`.
@freezed
class VersionStatus with _$VersionStatus {
  const factory VersionStatus({
    @JsonKey(unknownEnumValue: AppUpdateState.unknown)
    required AppUpdateState status,
    String? minSupportedVersion,
    String? latestVersion,
    String? storeUrl,
    String? message,
  }) = _VersionStatus;

  factory VersionStatus.fromJson(Map<String, dynamic> json) =>
      _$VersionStatusFromJson(json);
}
