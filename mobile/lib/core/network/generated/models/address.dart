import 'package:freezed_annotation/freezed_annotation.dart';

import 'geo_point.dart';

part 'address.freezed.dart';
part 'address.g.dart';

/// Espelha o schema `Address` de docs/api/openapi.yaml.
@freezed
class Address with _$Address {
  const factory Address({
    String? line1,
    String? line2,
    String? postalCode,
    String? city,
    String? regionCode,
    String? country,
    GeoPoint? location,
  }) = _Address;

  factory Address.fromJson(Map<String, dynamic> json) =>
      _$AddressFromJson(json);
}
