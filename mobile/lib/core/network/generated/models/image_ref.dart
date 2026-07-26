import 'package:freezed_annotation/freezed_annotation.dart';

part 'image_ref.freezed.dart';
part 'image_ref.g.dart';

/// Espelha o schema `ImageRef` de docs/api/openapi.yaml.
@freezed
class ImageRef with _$ImageRef {
  const factory ImageRef({
    required String id,
    required String url,
    String? contentType,
  }) = _ImageRef;

  factory ImageRef.fromJson(Map<String, dynamic> json) =>
      _$ImageRefFromJson(json);
}
