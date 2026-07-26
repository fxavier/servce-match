import 'package:freezed_annotation/freezed_annotation.dart';

part 'page_meta.freezed.dart';
part 'page_meta.g.dart';

/// Espelha o schema `PageMeta` (envelope de paginação por cursor).
@freezed
class PageMeta with _$PageMeta {
  const factory PageMeta({
    String? nextCursor,
  }) = _PageMeta;

  factory PageMeta.fromJson(Map<String, dynamic> json) =>
      _$PageMetaFromJson(json);
}
