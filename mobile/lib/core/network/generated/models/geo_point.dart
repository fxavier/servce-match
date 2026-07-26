import 'package:freezed_annotation/freezed_annotation.dart';

part 'geo_point.freezed.dart';
part 'geo_point.g.dart';

/// Espelha o schema `GeoPoint` (WGS84) de docs/api/openapi.yaml.
@freezed
class GeoPoint with _$GeoPoint {
  const factory GeoPoint({
    required double lat,
    required double lon,
  }) = _GeoPoint;

  factory GeoPoint.fromJson(Map<String, dynamic> json) =>
      _$GeoPointFromJson(json);
}
