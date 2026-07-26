// coverage:ignore-file
// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint
// ignore_for_file: unused_element, deprecated_member_use, deprecated_member_use_from_same_package, use_function_type_syntax_for_parameters, unnecessary_const, avoid_init_to_null, invalid_override_different_default_values_named, prefer_expression_function_bodies, annotate_overrides, invalid_annotation_target, unnecessary_question_mark

part of 'image_ref.dart';

// **************************************************************************
// FreezedGenerator
// **************************************************************************

T _$identity<T>(T value) => value;

final _privateConstructorUsedError = UnsupportedError(
  'It seems like you constructed your class using `MyClass._()`. This constructor is only meant to be used by freezed and you are not supposed to need it nor use it.\nPlease check the documentation here for more information: https://github.com/rrousselGit/freezed#adding-getters-and-methods-to-our-models',
);

ImageRef _$ImageRefFromJson(Map<String, dynamic> json) {
  return _ImageRef.fromJson(json);
}

/// @nodoc
mixin _$ImageRef {
  String get id => throw _privateConstructorUsedError;
  String get url => throw _privateConstructorUsedError;
  String? get contentType => throw _privateConstructorUsedError;

  /// Serializes this ImageRef to a JSON map.
  Map<String, dynamic> toJson() => throw _privateConstructorUsedError;

  /// Create a copy of ImageRef
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  $ImageRefCopyWith<ImageRef> get copyWith =>
      throw _privateConstructorUsedError;
}

/// @nodoc
abstract class $ImageRefCopyWith<$Res> {
  factory $ImageRefCopyWith(ImageRef value, $Res Function(ImageRef) then) =
      _$ImageRefCopyWithImpl<$Res, ImageRef>;
  @useResult
  $Res call({String id, String url, String? contentType});
}

/// @nodoc
class _$ImageRefCopyWithImpl<$Res, $Val extends ImageRef>
    implements $ImageRefCopyWith<$Res> {
  _$ImageRefCopyWithImpl(this._value, this._then);

  // ignore: unused_field
  final $Val _value;
  // ignore: unused_field
  final $Res Function($Val) _then;

  /// Create a copy of ImageRef
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  @override
  $Res call({
    Object? id = null,
    Object? url = null,
    Object? contentType = freezed,
  }) {
    return _then(
      _value.copyWith(
            id:
                null == id
                    ? _value.id
                    : id // ignore: cast_nullable_to_non_nullable
                        as String,
            url:
                null == url
                    ? _value.url
                    : url // ignore: cast_nullable_to_non_nullable
                        as String,
            contentType:
                freezed == contentType
                    ? _value.contentType
                    : contentType // ignore: cast_nullable_to_non_nullable
                        as String?,
          )
          as $Val,
    );
  }
}

/// @nodoc
abstract class _$$ImageRefImplCopyWith<$Res>
    implements $ImageRefCopyWith<$Res> {
  factory _$$ImageRefImplCopyWith(
    _$ImageRefImpl value,
    $Res Function(_$ImageRefImpl) then,
  ) = __$$ImageRefImplCopyWithImpl<$Res>;
  @override
  @useResult
  $Res call({String id, String url, String? contentType});
}

/// @nodoc
class __$$ImageRefImplCopyWithImpl<$Res>
    extends _$ImageRefCopyWithImpl<$Res, _$ImageRefImpl>
    implements _$$ImageRefImplCopyWith<$Res> {
  __$$ImageRefImplCopyWithImpl(
    _$ImageRefImpl _value,
    $Res Function(_$ImageRefImpl) _then,
  ) : super(_value, _then);

  /// Create a copy of ImageRef
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  @override
  $Res call({
    Object? id = null,
    Object? url = null,
    Object? contentType = freezed,
  }) {
    return _then(
      _$ImageRefImpl(
        id:
            null == id
                ? _value.id
                : id // ignore: cast_nullable_to_non_nullable
                    as String,
        url:
            null == url
                ? _value.url
                : url // ignore: cast_nullable_to_non_nullable
                    as String,
        contentType:
            freezed == contentType
                ? _value.contentType
                : contentType // ignore: cast_nullable_to_non_nullable
                    as String?,
      ),
    );
  }
}

/// @nodoc
@JsonSerializable()
class _$ImageRefImpl implements _ImageRef {
  const _$ImageRefImpl({required this.id, required this.url, this.contentType});

  factory _$ImageRefImpl.fromJson(Map<String, dynamic> json) =>
      _$$ImageRefImplFromJson(json);

  @override
  final String id;
  @override
  final String url;
  @override
  final String? contentType;

  @override
  String toString() {
    return 'ImageRef(id: $id, url: $url, contentType: $contentType)';
  }

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is _$ImageRefImpl &&
            (identical(other.id, id) || other.id == id) &&
            (identical(other.url, url) || other.url == url) &&
            (identical(other.contentType, contentType) ||
                other.contentType == contentType));
  }

  @JsonKey(includeFromJson: false, includeToJson: false)
  @override
  int get hashCode => Object.hash(runtimeType, id, url, contentType);

  /// Create a copy of ImageRef
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @override
  @pragma('vm:prefer-inline')
  _$$ImageRefImplCopyWith<_$ImageRefImpl> get copyWith =>
      __$$ImageRefImplCopyWithImpl<_$ImageRefImpl>(this, _$identity);

  @override
  Map<String, dynamic> toJson() {
    return _$$ImageRefImplToJson(this);
  }
}

abstract class _ImageRef implements ImageRef {
  const factory _ImageRef({
    required final String id,
    required final String url,
    final String? contentType,
  }) = _$ImageRefImpl;

  factory _ImageRef.fromJson(Map<String, dynamic> json) =
      _$ImageRefImpl.fromJson;

  @override
  String get id;
  @override
  String get url;
  @override
  String? get contentType;

  /// Create a copy of ImageRef
  /// with the given fields replaced by the non-null parameter values.
  @override
  @JsonKey(includeFromJson: false, includeToJson: false)
  _$$ImageRefImplCopyWith<_$ImageRefImpl> get copyWith =>
      throw _privateConstructorUsedError;
}
