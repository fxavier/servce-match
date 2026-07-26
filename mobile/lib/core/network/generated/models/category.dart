import 'package:freezed_annotation/freezed_annotation.dart';

part 'category.freezed.dart';
part 'category.g.dart';

/// Espelha o schema `Category` de docs/api/openapi.yaml.
@freezed
class Category with _$Category {
  const factory Category({
    required String id,
    String? parentId,
    required String slug,
    required String name,
    required bool active,
  }) = _Category;

  factory Category.fromJson(Map<String, dynamic> json) =>
      _$CategoryFromJson(json);
}
