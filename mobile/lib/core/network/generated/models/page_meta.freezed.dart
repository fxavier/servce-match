// coverage:ignore-file
// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint
// ignore_for_file: unused_element, deprecated_member_use, deprecated_member_use_from_same_package, use_function_type_syntax_for_parameters, unnecessary_const, avoid_init_to_null, invalid_override_different_default_values_named, prefer_expression_function_bodies, annotate_overrides, invalid_annotation_target, unnecessary_question_mark

part of 'page_meta.dart';

// **************************************************************************
// FreezedGenerator
// **************************************************************************

T _$identity<T>(T value) => value;

final _privateConstructorUsedError = UnsupportedError(
  'It seems like you constructed your class using `MyClass._()`. This constructor is only meant to be used by freezed and you are not supposed to need it nor use it.\nPlease check the documentation here for more information: https://github.com/rrousselGit/freezed#adding-getters-and-methods-to-our-models',
);

PageMeta _$PageMetaFromJson(Map<String, dynamic> json) {
  return _PageMeta.fromJson(json);
}

/// @nodoc
mixin _$PageMeta {
  String? get nextCursor => throw _privateConstructorUsedError;

  /// Serializes this PageMeta to a JSON map.
  Map<String, dynamic> toJson() => throw _privateConstructorUsedError;

  /// Create a copy of PageMeta
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  $PageMetaCopyWith<PageMeta> get copyWith =>
      throw _privateConstructorUsedError;
}

/// @nodoc
abstract class $PageMetaCopyWith<$Res> {
  factory $PageMetaCopyWith(PageMeta value, $Res Function(PageMeta) then) =
      _$PageMetaCopyWithImpl<$Res, PageMeta>;
  @useResult
  $Res call({String? nextCursor});
}

/// @nodoc
class _$PageMetaCopyWithImpl<$Res, $Val extends PageMeta>
    implements $PageMetaCopyWith<$Res> {
  _$PageMetaCopyWithImpl(this._value, this._then);

  // ignore: unused_field
  final $Val _value;
  // ignore: unused_field
  final $Res Function($Val) _then;

  /// Create a copy of PageMeta
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  @override
  $Res call({Object? nextCursor = freezed}) {
    return _then(
      _value.copyWith(
            nextCursor:
                freezed == nextCursor
                    ? _value.nextCursor
                    : nextCursor // ignore: cast_nullable_to_non_nullable
                        as String?,
          )
          as $Val,
    );
  }
}

/// @nodoc
abstract class _$$PageMetaImplCopyWith<$Res>
    implements $PageMetaCopyWith<$Res> {
  factory _$$PageMetaImplCopyWith(
    _$PageMetaImpl value,
    $Res Function(_$PageMetaImpl) then,
  ) = __$$PageMetaImplCopyWithImpl<$Res>;
  @override
  @useResult
  $Res call({String? nextCursor});
}

/// @nodoc
class __$$PageMetaImplCopyWithImpl<$Res>
    extends _$PageMetaCopyWithImpl<$Res, _$PageMetaImpl>
    implements _$$PageMetaImplCopyWith<$Res> {
  __$$PageMetaImplCopyWithImpl(
    _$PageMetaImpl _value,
    $Res Function(_$PageMetaImpl) _then,
  ) : super(_value, _then);

  /// Create a copy of PageMeta
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  @override
  $Res call({Object? nextCursor = freezed}) {
    return _then(
      _$PageMetaImpl(
        nextCursor:
            freezed == nextCursor
                ? _value.nextCursor
                : nextCursor // ignore: cast_nullable_to_non_nullable
                    as String?,
      ),
    );
  }
}

/// @nodoc
@JsonSerializable()
class _$PageMetaImpl implements _PageMeta {
  const _$PageMetaImpl({this.nextCursor});

  factory _$PageMetaImpl.fromJson(Map<String, dynamic> json) =>
      _$$PageMetaImplFromJson(json);

  @override
  final String? nextCursor;

  @override
  String toString() {
    return 'PageMeta(nextCursor: $nextCursor)';
  }

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is _$PageMetaImpl &&
            (identical(other.nextCursor, nextCursor) ||
                other.nextCursor == nextCursor));
  }

  @JsonKey(includeFromJson: false, includeToJson: false)
  @override
  int get hashCode => Object.hash(runtimeType, nextCursor);

  /// Create a copy of PageMeta
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @override
  @pragma('vm:prefer-inline')
  _$$PageMetaImplCopyWith<_$PageMetaImpl> get copyWith =>
      __$$PageMetaImplCopyWithImpl<_$PageMetaImpl>(this, _$identity);

  @override
  Map<String, dynamic> toJson() {
    return _$$PageMetaImplToJson(this);
  }
}

abstract class _PageMeta implements PageMeta {
  const factory _PageMeta({final String? nextCursor}) = _$PageMetaImpl;

  factory _PageMeta.fromJson(Map<String, dynamic> json) =
      _$PageMetaImpl.fromJson;

  @override
  String? get nextCursor;

  /// Create a copy of PageMeta
  /// with the given fields replaced by the non-null parameter values.
  @override
  @JsonKey(includeFromJson: false, includeToJson: false)
  _$$PageMetaImplCopyWith<_$PageMetaImpl> get copyWith =>
      throw _privateConstructorUsedError;
}
