// coverage:ignore-file
// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint
// ignore_for_file: unused_element, deprecated_member_use, deprecated_member_use_from_same_package, use_function_type_syntax_for_parameters, unnecessary_const, avoid_init_to_null, invalid_override_different_default_values_named, prefer_expression_function_bodies, annotate_overrides, invalid_annotation_target, unnecessary_question_mark

part of 'problem_details.dart';

// **************************************************************************
// FreezedGenerator
// **************************************************************************

T _$identity<T>(T value) => value;

final _privateConstructorUsedError = UnsupportedError(
  'It seems like you constructed your class using `MyClass._()`. This constructor is only meant to be used by freezed and you are not supposed to need it nor use it.\nPlease check the documentation here for more information: https://github.com/rrousselGit/freezed#adding-getters-and-methods-to-our-models',
);

ProblemDetails _$ProblemDetailsFromJson(Map<String, dynamic> json) {
  return _ProblemDetails.fromJson(json);
}

/// @nodoc
mixin _$ProblemDetails {
  String? get type => throw _privateConstructorUsedError;
  String get title => throw _privateConstructorUsedError;
  int get status => throw _privateConstructorUsedError;
  String? get detail => throw _privateConstructorUsedError;
  String? get instance => throw _privateConstructorUsedError;
  List<ProblemFieldError> get errors => throw _privateConstructorUsedError;

  /// Serializes this ProblemDetails to a JSON map.
  Map<String, dynamic> toJson() => throw _privateConstructorUsedError;

  /// Create a copy of ProblemDetails
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  $ProblemDetailsCopyWith<ProblemDetails> get copyWith =>
      throw _privateConstructorUsedError;
}

/// @nodoc
abstract class $ProblemDetailsCopyWith<$Res> {
  factory $ProblemDetailsCopyWith(
    ProblemDetails value,
    $Res Function(ProblemDetails) then,
  ) = _$ProblemDetailsCopyWithImpl<$Res, ProblemDetails>;
  @useResult
  $Res call({
    String? type,
    String title,
    int status,
    String? detail,
    String? instance,
    List<ProblemFieldError> errors,
  });
}

/// @nodoc
class _$ProblemDetailsCopyWithImpl<$Res, $Val extends ProblemDetails>
    implements $ProblemDetailsCopyWith<$Res> {
  _$ProblemDetailsCopyWithImpl(this._value, this._then);

  // ignore: unused_field
  final $Val _value;
  // ignore: unused_field
  final $Res Function($Val) _then;

  /// Create a copy of ProblemDetails
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  @override
  $Res call({
    Object? type = freezed,
    Object? title = null,
    Object? status = null,
    Object? detail = freezed,
    Object? instance = freezed,
    Object? errors = null,
  }) {
    return _then(
      _value.copyWith(
            type:
                freezed == type
                    ? _value.type
                    : type // ignore: cast_nullable_to_non_nullable
                        as String?,
            title:
                null == title
                    ? _value.title
                    : title // ignore: cast_nullable_to_non_nullable
                        as String,
            status:
                null == status
                    ? _value.status
                    : status // ignore: cast_nullable_to_non_nullable
                        as int,
            detail:
                freezed == detail
                    ? _value.detail
                    : detail // ignore: cast_nullable_to_non_nullable
                        as String?,
            instance:
                freezed == instance
                    ? _value.instance
                    : instance // ignore: cast_nullable_to_non_nullable
                        as String?,
            errors:
                null == errors
                    ? _value.errors
                    : errors // ignore: cast_nullable_to_non_nullable
                        as List<ProblemFieldError>,
          )
          as $Val,
    );
  }
}

/// @nodoc
abstract class _$$ProblemDetailsImplCopyWith<$Res>
    implements $ProblemDetailsCopyWith<$Res> {
  factory _$$ProblemDetailsImplCopyWith(
    _$ProblemDetailsImpl value,
    $Res Function(_$ProblemDetailsImpl) then,
  ) = __$$ProblemDetailsImplCopyWithImpl<$Res>;
  @override
  @useResult
  $Res call({
    String? type,
    String title,
    int status,
    String? detail,
    String? instance,
    List<ProblemFieldError> errors,
  });
}

/// @nodoc
class __$$ProblemDetailsImplCopyWithImpl<$Res>
    extends _$ProblemDetailsCopyWithImpl<$Res, _$ProblemDetailsImpl>
    implements _$$ProblemDetailsImplCopyWith<$Res> {
  __$$ProblemDetailsImplCopyWithImpl(
    _$ProblemDetailsImpl _value,
    $Res Function(_$ProblemDetailsImpl) _then,
  ) : super(_value, _then);

  /// Create a copy of ProblemDetails
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  @override
  $Res call({
    Object? type = freezed,
    Object? title = null,
    Object? status = null,
    Object? detail = freezed,
    Object? instance = freezed,
    Object? errors = null,
  }) {
    return _then(
      _$ProblemDetailsImpl(
        type:
            freezed == type
                ? _value.type
                : type // ignore: cast_nullable_to_non_nullable
                    as String?,
        title:
            null == title
                ? _value.title
                : title // ignore: cast_nullable_to_non_nullable
                    as String,
        status:
            null == status
                ? _value.status
                : status // ignore: cast_nullable_to_non_nullable
                    as int,
        detail:
            freezed == detail
                ? _value.detail
                : detail // ignore: cast_nullable_to_non_nullable
                    as String?,
        instance:
            freezed == instance
                ? _value.instance
                : instance // ignore: cast_nullable_to_non_nullable
                    as String?,
        errors:
            null == errors
                ? _value._errors
                : errors // ignore: cast_nullable_to_non_nullable
                    as List<ProblemFieldError>,
      ),
    );
  }
}

/// @nodoc
@JsonSerializable()
class _$ProblemDetailsImpl implements _ProblemDetails {
  const _$ProblemDetailsImpl({
    this.type,
    required this.title,
    required this.status,
    this.detail,
    this.instance,
    final List<ProblemFieldError> errors = const <ProblemFieldError>[],
  }) : _errors = errors;

  factory _$ProblemDetailsImpl.fromJson(Map<String, dynamic> json) =>
      _$$ProblemDetailsImplFromJson(json);

  @override
  final String? type;
  @override
  final String title;
  @override
  final int status;
  @override
  final String? detail;
  @override
  final String? instance;
  final List<ProblemFieldError> _errors;
  @override
  @JsonKey()
  List<ProblemFieldError> get errors {
    if (_errors is EqualUnmodifiableListView) return _errors;
    // ignore: implicit_dynamic_type
    return EqualUnmodifiableListView(_errors);
  }

  @override
  String toString() {
    return 'ProblemDetails(type: $type, title: $title, status: $status, detail: $detail, instance: $instance, errors: $errors)';
  }

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is _$ProblemDetailsImpl &&
            (identical(other.type, type) || other.type == type) &&
            (identical(other.title, title) || other.title == title) &&
            (identical(other.status, status) || other.status == status) &&
            (identical(other.detail, detail) || other.detail == detail) &&
            (identical(other.instance, instance) ||
                other.instance == instance) &&
            const DeepCollectionEquality().equals(other._errors, _errors));
  }

  @JsonKey(includeFromJson: false, includeToJson: false)
  @override
  int get hashCode => Object.hash(
    runtimeType,
    type,
    title,
    status,
    detail,
    instance,
    const DeepCollectionEquality().hash(_errors),
  );

  /// Create a copy of ProblemDetails
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @override
  @pragma('vm:prefer-inline')
  _$$ProblemDetailsImplCopyWith<_$ProblemDetailsImpl> get copyWith =>
      __$$ProblemDetailsImplCopyWithImpl<_$ProblemDetailsImpl>(
        this,
        _$identity,
      );

  @override
  Map<String, dynamic> toJson() {
    return _$$ProblemDetailsImplToJson(this);
  }
}

abstract class _ProblemDetails implements ProblemDetails {
  const factory _ProblemDetails({
    final String? type,
    required final String title,
    required final int status,
    final String? detail,
    final String? instance,
    final List<ProblemFieldError> errors,
  }) = _$ProblemDetailsImpl;

  factory _ProblemDetails.fromJson(Map<String, dynamic> json) =
      _$ProblemDetailsImpl.fromJson;

  @override
  String? get type;
  @override
  String get title;
  @override
  int get status;
  @override
  String? get detail;
  @override
  String? get instance;
  @override
  List<ProblemFieldError> get errors;

  /// Create a copy of ProblemDetails
  /// with the given fields replaced by the non-null parameter values.
  @override
  @JsonKey(includeFromJson: false, includeToJson: false)
  _$$ProblemDetailsImplCopyWith<_$ProblemDetailsImpl> get copyWith =>
      throw _privateConstructorUsedError;
}

ProblemFieldError _$ProblemFieldErrorFromJson(Map<String, dynamic> json) {
  return _ProblemFieldError.fromJson(json);
}

/// @nodoc
mixin _$ProblemFieldError {
  String? get field => throw _privateConstructorUsedError;
  String? get message => throw _privateConstructorUsedError;

  /// Serializes this ProblemFieldError to a JSON map.
  Map<String, dynamic> toJson() => throw _privateConstructorUsedError;

  /// Create a copy of ProblemFieldError
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  $ProblemFieldErrorCopyWith<ProblemFieldError> get copyWith =>
      throw _privateConstructorUsedError;
}

/// @nodoc
abstract class $ProblemFieldErrorCopyWith<$Res> {
  factory $ProblemFieldErrorCopyWith(
    ProblemFieldError value,
    $Res Function(ProblemFieldError) then,
  ) = _$ProblemFieldErrorCopyWithImpl<$Res, ProblemFieldError>;
  @useResult
  $Res call({String? field, String? message});
}

/// @nodoc
class _$ProblemFieldErrorCopyWithImpl<$Res, $Val extends ProblemFieldError>
    implements $ProblemFieldErrorCopyWith<$Res> {
  _$ProblemFieldErrorCopyWithImpl(this._value, this._then);

  // ignore: unused_field
  final $Val _value;
  // ignore: unused_field
  final $Res Function($Val) _then;

  /// Create a copy of ProblemFieldError
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  @override
  $Res call({Object? field = freezed, Object? message = freezed}) {
    return _then(
      _value.copyWith(
            field:
                freezed == field
                    ? _value.field
                    : field // ignore: cast_nullable_to_non_nullable
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
abstract class _$$ProblemFieldErrorImplCopyWith<$Res>
    implements $ProblemFieldErrorCopyWith<$Res> {
  factory _$$ProblemFieldErrorImplCopyWith(
    _$ProblemFieldErrorImpl value,
    $Res Function(_$ProblemFieldErrorImpl) then,
  ) = __$$ProblemFieldErrorImplCopyWithImpl<$Res>;
  @override
  @useResult
  $Res call({String? field, String? message});
}

/// @nodoc
class __$$ProblemFieldErrorImplCopyWithImpl<$Res>
    extends _$ProblemFieldErrorCopyWithImpl<$Res, _$ProblemFieldErrorImpl>
    implements _$$ProblemFieldErrorImplCopyWith<$Res> {
  __$$ProblemFieldErrorImplCopyWithImpl(
    _$ProblemFieldErrorImpl _value,
    $Res Function(_$ProblemFieldErrorImpl) _then,
  ) : super(_value, _then);

  /// Create a copy of ProblemFieldError
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  @override
  $Res call({Object? field = freezed, Object? message = freezed}) {
    return _then(
      _$ProblemFieldErrorImpl(
        field:
            freezed == field
                ? _value.field
                : field // ignore: cast_nullable_to_non_nullable
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
class _$ProblemFieldErrorImpl implements _ProblemFieldError {
  const _$ProblemFieldErrorImpl({this.field, this.message});

  factory _$ProblemFieldErrorImpl.fromJson(Map<String, dynamic> json) =>
      _$$ProblemFieldErrorImplFromJson(json);

  @override
  final String? field;
  @override
  final String? message;

  @override
  String toString() {
    return 'ProblemFieldError(field: $field, message: $message)';
  }

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is _$ProblemFieldErrorImpl &&
            (identical(other.field, field) || other.field == field) &&
            (identical(other.message, message) || other.message == message));
  }

  @JsonKey(includeFromJson: false, includeToJson: false)
  @override
  int get hashCode => Object.hash(runtimeType, field, message);

  /// Create a copy of ProblemFieldError
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @override
  @pragma('vm:prefer-inline')
  _$$ProblemFieldErrorImplCopyWith<_$ProblemFieldErrorImpl> get copyWith =>
      __$$ProblemFieldErrorImplCopyWithImpl<_$ProblemFieldErrorImpl>(
        this,
        _$identity,
      );

  @override
  Map<String, dynamic> toJson() {
    return _$$ProblemFieldErrorImplToJson(this);
  }
}

abstract class _ProblemFieldError implements ProblemFieldError {
  const factory _ProblemFieldError({
    final String? field,
    final String? message,
  }) = _$ProblemFieldErrorImpl;

  factory _ProblemFieldError.fromJson(Map<String, dynamic> json) =
      _$ProblemFieldErrorImpl.fromJson;

  @override
  String? get field;
  @override
  String? get message;

  /// Create a copy of ProblemFieldError
  /// with the given fields replaced by the non-null parameter values.
  @override
  @JsonKey(includeFromJson: false, includeToJson: false)
  _$$ProblemFieldErrorImplCopyWith<_$ProblemFieldErrorImpl> get copyWith =>
      throw _privateConstructorUsedError;
}
