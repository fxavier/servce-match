// coverage:ignore-file
// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint
// ignore_for_file: unused_element, deprecated_member_use, deprecated_member_use_from_same_package, use_function_type_syntax_for_parameters, unnecessary_const, avoid_init_to_null, invalid_override_different_default_values_named, prefer_expression_function_bodies, annotate_overrides, invalid_annotation_target, unnecessary_question_mark

part of 'version_status.dart';

// **************************************************************************
// FreezedGenerator
// **************************************************************************

T _$identity<T>(T value) => value;

final _privateConstructorUsedError = UnsupportedError(
  'It seems like you constructed your class using `MyClass._()`. This constructor is only meant to be used by freezed and you are not supposed to need it nor use it.\nPlease check the documentation here for more information: https://github.com/rrousselGit/freezed#adding-getters-and-methods-to-our-models',
);

VersionStatus _$VersionStatusFromJson(Map<String, dynamic> json) {
  return _VersionStatus.fromJson(json);
}

/// @nodoc
mixin _$VersionStatus {
  @JsonKey(unknownEnumValue: AppUpdateState.unknown)
  AppUpdateState get status => throw _privateConstructorUsedError;
  String? get minSupportedVersion => throw _privateConstructorUsedError;
  String? get latestVersion => throw _privateConstructorUsedError;
  String? get storeUrl => throw _privateConstructorUsedError;
  String? get message => throw _privateConstructorUsedError;

  /// Serializes this VersionStatus to a JSON map.
  Map<String, dynamic> toJson() => throw _privateConstructorUsedError;

  /// Create a copy of VersionStatus
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  $VersionStatusCopyWith<VersionStatus> get copyWith =>
      throw _privateConstructorUsedError;
}

/// @nodoc
abstract class $VersionStatusCopyWith<$Res> {
  factory $VersionStatusCopyWith(
    VersionStatus value,
    $Res Function(VersionStatus) then,
  ) = _$VersionStatusCopyWithImpl<$Res, VersionStatus>;
  @useResult
  $Res call({
    @JsonKey(unknownEnumValue: AppUpdateState.unknown) AppUpdateState status,
    String? minSupportedVersion,
    String? latestVersion,
    String? storeUrl,
    String? message,
  });
}

/// @nodoc
class _$VersionStatusCopyWithImpl<$Res, $Val extends VersionStatus>
    implements $VersionStatusCopyWith<$Res> {
  _$VersionStatusCopyWithImpl(this._value, this._then);

  // ignore: unused_field
  final $Val _value;
  // ignore: unused_field
  final $Res Function($Val) _then;

  /// Create a copy of VersionStatus
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  @override
  $Res call({
    Object? status = null,
    Object? minSupportedVersion = freezed,
    Object? latestVersion = freezed,
    Object? storeUrl = freezed,
    Object? message = freezed,
  }) {
    return _then(
      _value.copyWith(
            status:
                null == status
                    ? _value.status
                    : status // ignore: cast_nullable_to_non_nullable
                        as AppUpdateState,
            minSupportedVersion:
                freezed == minSupportedVersion
                    ? _value.minSupportedVersion
                    : minSupportedVersion // ignore: cast_nullable_to_non_nullable
                        as String?,
            latestVersion:
                freezed == latestVersion
                    ? _value.latestVersion
                    : latestVersion // ignore: cast_nullable_to_non_nullable
                        as String?,
            storeUrl:
                freezed == storeUrl
                    ? _value.storeUrl
                    : storeUrl // ignore: cast_nullable_to_non_nullable
                        as String?,
            message:
                freezed == message
                    ? _value.message
                    : message // ignore: cast_nullable_to_non_nullable
                        as String?,
          )
          as $Val,
    );
  }
}

/// @nodoc
abstract class _$$VersionStatusImplCopyWith<$Res>
    implements $VersionStatusCopyWith<$Res> {
  factory _$$VersionStatusImplCopyWith(
    _$VersionStatusImpl value,
    $Res Function(_$VersionStatusImpl) then,
  ) = __$$VersionStatusImplCopyWithImpl<$Res>;
  @override
  @useResult
  $Res call({
    @JsonKey(unknownEnumValue: AppUpdateState.unknown) AppUpdateState status,
    String? minSupportedVersion,
    String? latestVersion,
    String? storeUrl,
    String? message,
  });
}

/// @nodoc
class __$$VersionStatusImplCopyWithImpl<$Res>
    extends _$VersionStatusCopyWithImpl<$Res, _$VersionStatusImpl>
    implements _$$VersionStatusImplCopyWith<$Res> {
  __$$VersionStatusImplCopyWithImpl(
    _$VersionStatusImpl _value,
    $Res Function(_$VersionStatusImpl) _then,
  ) : super(_value, _then);

  /// Create a copy of VersionStatus
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  @override
  $Res call({
    Object? status = null,
    Object? minSupportedVersion = freezed,
    Object? latestVersion = freezed,
    Object? storeUrl = freezed,
    Object? message = freezed,
  }) {
    return _then(
      _$VersionStatusImpl(
        status:
            null == status
                ? _value.status
                : status // ignore: cast_nullable_to_non_nullable
                    as AppUpdateState,
        minSupportedVersion:
            freezed == minSupportedVersion
                ? _value.minSupportedVersion
                : minSupportedVersion // ignore: cast_nullable_to_non_nullable
                    as String?,
        latestVersion:
            freezed == latestVersion
                ? _value.latestVersion
                : latestVersion // ignore: cast_nullable_to_non_nullable
                    as String?,
        storeUrl:
            freezed == storeUrl
                ? _value.storeUrl
                : storeUrl // ignore: cast_nullable_to_non_nullable
                    as String?,
        message:
            freezed == message
                ? _value.message
                : message // ignore: cast_nullable_to_non_nullable
                    as String?,
      ),
    );
  }
}

/// @nodoc
@JsonSerializable()
class _$VersionStatusImpl implements _VersionStatus {
  const _$VersionStatusImpl({
    @JsonKey(unknownEnumValue: AppUpdateState.unknown) required this.status,
    this.minSupportedVersion,
    this.latestVersion,
    this.storeUrl,
    this.message,
  });

  factory _$VersionStatusImpl.fromJson(Map<String, dynamic> json) =>
      _$$VersionStatusImplFromJson(json);

  @override
  @JsonKey(unknownEnumValue: AppUpdateState.unknown)
  final AppUpdateState status;
  @override
  final String? minSupportedVersion;
  @override
  final String? latestVersion;
  @override
  final String? storeUrl;
  @override
  final String? message;

  @override
  String toString() {
    return 'VersionStatus(status: $status, minSupportedVersion: $minSupportedVersion, latestVersion: $latestVersion, storeUrl: $storeUrl, message: $message)';
  }

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is _$VersionStatusImpl &&
            (identical(other.status, status) || other.status == status) &&
            (identical(other.minSupportedVersion, minSupportedVersion) ||
                other.minSupportedVersion == minSupportedVersion) &&
            (identical(other.latestVersion, latestVersion) ||
                other.latestVersion == latestVersion) &&
            (identical(other.storeUrl, storeUrl) ||
                other.storeUrl == storeUrl) &&
            (identical(other.message, message) || other.message == message));
  }

  @JsonKey(includeFromJson: false, includeToJson: false)
  @override
  int get hashCode => Object.hash(
    runtimeType,
    status,
    minSupportedVersion,
    latestVersion,
    storeUrl,
    message,
  );

  /// Create a copy of VersionStatus
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @override
  @pragma('vm:prefer-inline')
  _$$VersionStatusImplCopyWith<_$VersionStatusImpl> get copyWith =>
      __$$VersionStatusImplCopyWithImpl<_$VersionStatusImpl>(this, _$identity);

  @override
  Map<String, dynamic> toJson() {
    return _$$VersionStatusImplToJson(this);
  }
}

abstract class _VersionStatus implements VersionStatus {
  const factory _VersionStatus({
    @JsonKey(unknownEnumValue: AppUpdateState.unknown)
    required final AppUpdateState status,
    final String? minSupportedVersion,
    final String? latestVersion,
    final String? storeUrl,
    final String? message,
  }) = _$VersionStatusImpl;

  factory _VersionStatus.fromJson(Map<String, dynamic> json) =
      _$VersionStatusImpl.fromJson;

  @override
  @JsonKey(unknownEnumValue: AppUpdateState.unknown)
  AppUpdateState get status;
  @override
  String? get minSupportedVersion;
  @override
  String? get latestVersion;
  @override
  String? get storeUrl;
  @override
  String? get message;

  /// Create a copy of VersionStatus
  /// with the given fields replaced by the non-null parameter values.
  @override
  @JsonKey(includeFromJson: false, includeToJson: false)
  _$$VersionStatusImplCopyWith<_$VersionStatusImpl> get copyWith =>
      throw _privateConstructorUsedError;
}
