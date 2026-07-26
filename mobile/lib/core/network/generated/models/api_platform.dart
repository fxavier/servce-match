import 'package:json_annotation/json_annotation.dart';

/// Espelha o schema `Platform` de docs/api/openapi.yaml.
///
/// Chama-se `ApiPlatform` (e não `Platform`) para não colidir com
/// `dart:io`'s `Platform`.
enum ApiPlatform {
  @JsonValue('IOS')
  ios,
  @JsonValue('ANDROID')
  android,
  @JsonValue('WEB')
  web;

  String get wireValue => switch (this) {
        ApiPlatform.ios => 'IOS',
        ApiPlatform.android => 'ANDROID',
        ApiPlatform.web => 'WEB',
      };
}
