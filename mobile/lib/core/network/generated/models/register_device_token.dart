import 'package:freezed_annotation/freezed_annotation.dart';

import 'api_platform.dart';

part 'register_device_token.freezed.dart';
part 'register_device_token.g.dart';

/// Espelha o schema `RegisterDeviceToken` de docs/api/openapi.yaml.
@freezed
class RegisterDeviceToken with _$RegisterDeviceToken {
  const factory RegisterDeviceToken({
    required String token,
    required ApiPlatform platform,
    String? appVersion,
  }) = _RegisterDeviceToken;

  factory RegisterDeviceToken.fromJson(Map<String, dynamic> json) =>
      _$RegisterDeviceTokenFromJson(json);
}
