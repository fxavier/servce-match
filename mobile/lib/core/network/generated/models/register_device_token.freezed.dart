// coverage:ignore-file
// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint
// ignore_for_file: unused_element, deprecated_member_use, deprecated_member_use_from_same_package, use_function_type_syntax_for_parameters, unnecessary_const, avoid_init_to_null, invalid_override_different_default_values_named, prefer_expression_function_bodies, annotate_overrides, invalid_annotation_target, unnecessary_question_mark

part of 'register_device_token.dart';

// **************************************************************************
// FreezedGenerator
// **************************************************************************

T _$identity<T>(T value) => value;

final _privateConstructorUsedError = UnsupportedError(
  'It seems like you constructed your class using `MyClass._()`. This constructor is only meant to be used by freezed and you are not supposed to need it nor use it.\nPlease check the documentation here for more information: https://github.com/rrousselGit/freezed#adding-getters-and-methods-to-our-models',
);

RegisterDeviceToken _$RegisterDeviceTokenFromJson(Map<String, dynamic> json) {
  return _RegisterDeviceToken.fromJson(json);
}

/// @nodoc
mixin _$RegisterDeviceToken {
  String get token => throw _privateConstructorUsedError;
  ApiPlatform get platform => throw _privateConstructorUsedError;
  String? get appVersion => throw _privateConstructorUsedError;

  /// Serializes this RegisterDeviceToken to a JSON map.
  Map<String, dynamic> toJson() => throw _privateConstructorUsedError;

  /// Create a copy of RegisterDeviceToken
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  $RegisterDeviceTokenCopyWith<RegisterDeviceToken> get copyWith =>
      throw _privateConstructorUsedError;
}

/// @nodoc
abstract class $RegisterDeviceTokenCopyWith<$Res> {
  factory $RegisterDeviceTokenCopyWith(
    RegisterDeviceToken value,
    $Res Function(RegisterDeviceToken) then,
  ) = _$RegisterDeviceTokenCopyWithImpl<$Res, RegisterDeviceToken>;
  @useResult
  $Res call({String token, ApiPlatform platform, String? appVersion});
}

/// @nodoc
class _$RegisterDeviceTokenCopyWithImpl<$Res, $Val extends RegisterDeviceToken>
    implements $RegisterDeviceTokenCopyWith<$Res> {
  _$RegisterDeviceTokenCopyWithImpl(this._value, this._then);

  // ignore: unused_field
  final $Val _value;
  // ignore: unused_field
  final $Res Function($Val) _then;

  /// Create a copy of RegisterDeviceToken
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  @override
  $Res call({
    Object? token = null,
    Object? platform = null,
    Object? appVersion = freezed,
  }) {
    return _then(
      _value.copyWith(
            token:
                null == token
                    ? _value.token
                    : token // ignore: cast_nullable_to_non_nullable
                        as String,
            platform:
                null == platform
                    ? _value.platform
                    : platform // ignore: cast_nullable_to_non_nullable
                        as ApiPlatform,
            appVersion:
                freezed == appVersion
                    ? _value.appVersion
                    : appVersion // ignore: cast_nullable_to_non_nullable
                        as String?,
          )
          as $Val,
    );
  }
}

/// @nodoc
abstract class _$$RegisterDeviceTokenImplCopyWith<$Res>
    implements $RegisterDeviceTokenCopyWith<$Res> {
  factory _$$RegisterDeviceTokenImplCopyWith(
    _$RegisterDeviceTokenImpl value,
    $Res Function(_$RegisterDeviceTokenImpl) then,
  ) = __$$RegisterDeviceTokenImplCopyWithImpl<$Res>;
  @override
  @useResult
  $Res call({String token, ApiPlatform platform, String? appVersion});
}

/// @nodoc
class __$$RegisterDeviceTokenImplCopyWithImpl<$Res>
    extends _$RegisterDeviceTokenCopyWithImpl<$Res, _$RegisterDeviceTokenImpl>
    implements _$$RegisterDeviceTokenImplCopyWith<$Res> {
  __$$RegisterDeviceTokenImplCopyWithImpl(
    _$RegisterDeviceTokenImpl _value,
    $Res Function(_$RegisterDeviceTokenImpl) _then,
  ) : super(_value, _then);

  /// Create a copy of RegisterDeviceToken
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  @override
  $Res call({
    Object? token = null,
    Object? platform = null,
    Object? appVersion = freezed,
  }) {
    return _then(
      _$RegisterDeviceTokenImpl(
        token:
            null == token
                ? _value.token
                : token // ignore: cast_nullable_to_non_nullable
                    as String,
        platform:
            null == platform
                ? _value.platform
                : platform // ignore: cast_nullable_to_non_nullable
                    as ApiPlatform,
        appVersion:
            freezed == appVersion
                ? _value.appVersion
                : appVersion // ignore: cast_nullable_to_non_nullable
                    as String?,
      ),
    );
  }
}

/// @nodoc
@JsonSerializable()
class _$RegisterDeviceTokenImpl implements _RegisterDeviceToken {
  const _$RegisterDeviceTokenImpl({
    required this.token,
    required this.platform,
    this.appVersion,
  });

  factory _$RegisterDeviceTokenImpl.fromJson(Map<String, dynamic> json) =>
      _$$RegisterDeviceTokenImplFromJson(json);

  @override
  final String token;
  @override
  final ApiPlatform platform;
  @override
  final String? appVersion;

  @override
  String toString() {
    return 'RegisterDeviceToken(token: $token, platform: $platform, appVersion: $appVersion)';
  }

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is _$RegisterDeviceTokenImpl &&
            (identical(other.token, token) || other.token == token) &&
            (identical(other.platform, platform) ||
                other.platform == platform) &&
            (identical(other.appVersion, appVersion) ||
                other.appVersion == appVersion));
  }

  @JsonKey(includeFromJson: false, includeToJson: false)
  @override
  int get hashCode => Object.hash(runtimeType, token, platform, appVersion);

  /// Create a copy of RegisterDeviceToken
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @override
  @pragma('vm:prefer-inline')
  _$$RegisterDeviceTokenImplCopyWith<_$RegisterDeviceTokenImpl> get copyWith =>
      __$$RegisterDeviceTokenImplCopyWithImpl<_$RegisterDeviceTokenImpl>(
        this,
        _$identity,
      );

  @override
  Map<String, dynamic> toJson() {
    return _$$RegisterDeviceTokenImplToJson(this);
  }
}

abstract class _RegisterDeviceToken implements RegisterDeviceToken {
  const factory _RegisterDeviceToken({
    required final String token,
    required final ApiPlatform platform,
    final String? appVersion,
  }) = _$RegisterDeviceTokenImpl;

  factory _RegisterDeviceToken.fromJson(Map<String, dynamic> json) =
      _$RegisterDeviceTokenImpl.fromJson;

  @override
  String get token;
  @override
  ApiPlatform get platform;
  @override
  String? get appVersion;

  /// Create a copy of RegisterDeviceToken
  /// with the given fields replaced by the non-null parameter values.
  @override
  @JsonKey(includeFromJson: false, includeToJson: false)
  _$$RegisterDeviceTokenImplCopyWith<_$RegisterDeviceTokenImpl> get copyWith =>
      throw _privateConstructorUsedError;
}
