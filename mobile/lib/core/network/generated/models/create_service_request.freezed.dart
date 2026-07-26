// coverage:ignore-file
// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint
// ignore_for_file: unused_element, deprecated_member_use, deprecated_member_use_from_same_package, use_function_type_syntax_for_parameters, unnecessary_const, avoid_init_to_null, invalid_override_different_default_values_named, prefer_expression_function_bodies, annotate_overrides, invalid_annotation_target, unnecessary_question_mark

part of 'create_service_request.dart';

// **************************************************************************
// FreezedGenerator
// **************************************************************************

T _$identity<T>(T value) => value;

final _privateConstructorUsedError = UnsupportedError(
  'It seems like you constructed your class using `MyClass._()`. This constructor is only meant to be used by freezed and you are not supposed to need it nor use it.\nPlease check the documentation here for more information: https://github.com/rrousselGit/freezed#adding-getters-and-methods-to-our-models',
);

CreateServiceRequest _$CreateServiceRequestFromJson(Map<String, dynamic> json) {
  return _CreateServiceRequest.fromJson(json);
}

/// @nodoc
mixin _$CreateServiceRequest {
  String get categoryId => throw _privateConstructorUsedError;
  String get title => throw _privateConstructorUsedError;
  String? get description => throw _privateConstructorUsedError;
  Address get address => throw _privateConstructorUsedError;
  UrgencyLevel? get urgency => throw _privateConstructorUsedError;
  String? get availability => throw _privateConstructorUsedError;
  List<String> get imageIds => throw _privateConstructorUsedError;

  /// Serializes this CreateServiceRequest to a JSON map.
  Map<String, dynamic> toJson() => throw _privateConstructorUsedError;

  /// Create a copy of CreateServiceRequest
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  $CreateServiceRequestCopyWith<CreateServiceRequest> get copyWith =>
      throw _privateConstructorUsedError;
}

/// @nodoc
abstract class $CreateServiceRequestCopyWith<$Res> {
  factory $CreateServiceRequestCopyWith(
    CreateServiceRequest value,
    $Res Function(CreateServiceRequest) then,
  ) = _$CreateServiceRequestCopyWithImpl<$Res, CreateServiceRequest>;
  @useResult
  $Res call({
    String categoryId,
    String title,
    String? description,
    Address address,
    UrgencyLevel? urgency,
    String? availability,
    List<String> imageIds,
  });

  $AddressCopyWith<$Res> get address;
}

/// @nodoc
class _$CreateServiceRequestCopyWithImpl<
  $Res,
  $Val extends CreateServiceRequest
>
    implements $CreateServiceRequestCopyWith<$Res> {
  _$CreateServiceRequestCopyWithImpl(this._value, this._then);

  // ignore: unused_field
  final $Val _value;
  // ignore: unused_field
  final $Res Function($Val) _then;

  /// Create a copy of CreateServiceRequest
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  @override
  $Res call({
    Object? categoryId = null,
    Object? title = null,
    Object? description = freezed,
    Object? address = null,
    Object? urgency = freezed,
    Object? availability = freezed,
    Object? imageIds = null,
  }) {
    return _then(
      _value.copyWith(
            categoryId:
                null == categoryId
                    ? _value.categoryId
                    : categoryId // ignore: cast_nullable_to_non_nullable
                        as String,
            title:
                null == title
                    ? _value.title
                    : title // ignore: cast_nullable_to_non_nullable
                        as String,
            description:
                freezed == description
                    ? _value.description
                    : description // ignore: cast_nullable_to_non_nullable
                        as String?,
            address:
                null == address
                    ? _value.address
                    : address // ignore: cast_nullable_to_non_nullable
                        as Address,
            urgency:
                freezed == urgency
                    ? _value.urgency
                    : urgency // ignore: cast_nullable_to_non_nullable
                        as UrgencyLevel?,
            availability:
                freezed == availability
                    ? _value.availability
                    : availability // ignore: cast_nullable_to_non_nullable
                        as String?,
            imageIds:
                null == imageIds
                    ? _value.imageIds
                    : imageIds // ignore: cast_nullable_to_non_nullable
                        as List<String>,
          )
          as $Val,
    );
  }

  /// Create a copy of CreateServiceRequest
  /// with the given fields replaced by the non-null parameter values.
  @override
  @pragma('vm:prefer-inline')
  $AddressCopyWith<$Res> get address {
    return $AddressCopyWith<$Res>(_value.address, (value) {
      return _then(_value.copyWith(address: value) as $Val);
    });
  }
}

/// @nodoc
abstract class _$$CreateServiceRequestImplCopyWith<$Res>
    implements $CreateServiceRequestCopyWith<$Res> {
  factory _$$CreateServiceRequestImplCopyWith(
    _$CreateServiceRequestImpl value,
    $Res Function(_$CreateServiceRequestImpl) then,
  ) = __$$CreateServiceRequestImplCopyWithImpl<$Res>;
  @override
  @useResult
  $Res call({
    String categoryId,
    String title,
    String? description,
    Address address,
    UrgencyLevel? urgency,
    String? availability,
    List<String> imageIds,
  });

  @override
  $AddressCopyWith<$Res> get address;
}

/// @nodoc
class __$$CreateServiceRequestImplCopyWithImpl<$Res>
    extends _$CreateServiceRequestCopyWithImpl<$Res, _$CreateServiceRequestImpl>
    implements _$$CreateServiceRequestImplCopyWith<$Res> {
  __$$CreateServiceRequestImplCopyWithImpl(
    _$CreateServiceRequestImpl _value,
    $Res Function(_$CreateServiceRequestImpl) _then,
  ) : super(_value, _then);

  /// Create a copy of CreateServiceRequest
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  @override
  $Res call({
    Object? categoryId = null,
    Object? title = null,
    Object? description = freezed,
    Object? address = null,
    Object? urgency = freezed,
    Object? availability = freezed,
    Object? imageIds = null,
  }) {
    return _then(
      _$CreateServiceRequestImpl(
        categoryId:
            null == categoryId
                ? _value.categoryId
                : categoryId // ignore: cast_nullable_to_non_nullable
                    as String,
        title:
            null == title
                ? _value.title
                : title // ignore: cast_nullable_to_non_nullable
                    as String,
        description:
            freezed == description
                ? _value.description
                : description // ignore: cast_nullable_to_non_nullable
                    as String?,
        address:
            null == address
                ? _value.address
                : address // ignore: cast_nullable_to_non_nullable
                    as Address,
        urgency:
            freezed == urgency
                ? _value.urgency
                : urgency // ignore: cast_nullable_to_non_nullable
                    as UrgencyLevel?,
        availability:
            freezed == availability
                ? _value.availability
                : availability // ignore: cast_nullable_to_non_nullable
                    as String?,
        imageIds:
            null == imageIds
                ? _value._imageIds
                : imageIds // ignore: cast_nullable_to_non_nullable
                    as List<String>,
      ),
    );
  }
}

/// @nodoc
@JsonSerializable()
class _$CreateServiceRequestImpl implements _CreateServiceRequest {
  const _$CreateServiceRequestImpl({
    required this.categoryId,
    required this.title,
    this.description,
    required this.address,
    this.urgency,
    this.availability,
    final List<String> imageIds = const <String>[],
  }) : _imageIds = imageIds;

  factory _$CreateServiceRequestImpl.fromJson(Map<String, dynamic> json) =>
      _$$CreateServiceRequestImplFromJson(json);

  @override
  final String categoryId;
  @override
  final String title;
  @override
  final String? description;
  @override
  final Address address;
  @override
  final UrgencyLevel? urgency;
  @override
  final String? availability;
  final List<String> _imageIds;
  @override
  @JsonKey()
  List<String> get imageIds {
    if (_imageIds is EqualUnmodifiableListView) return _imageIds;
    // ignore: implicit_dynamic_type
    return EqualUnmodifiableListView(_imageIds);
  }

  @override
  String toString() {
    return 'CreateServiceRequest(categoryId: $categoryId, title: $title, description: $description, address: $address, urgency: $urgency, availability: $availability, imageIds: $imageIds)';
  }

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is _$CreateServiceRequestImpl &&
            (identical(other.categoryId, categoryId) ||
                other.categoryId == categoryId) &&
            (identical(other.title, title) || other.title == title) &&
            (identical(other.description, description) ||
                other.description == description) &&
            (identical(other.address, address) || other.address == address) &&
            (identical(other.urgency, urgency) || other.urgency == urgency) &&
            (identical(other.availability, availability) ||
                other.availability == availability) &&
            const DeepCollectionEquality().equals(other._imageIds, _imageIds));
  }

  @JsonKey(includeFromJson: false, includeToJson: false)
  @override
  int get hashCode => Object.hash(
    runtimeType,
    categoryId,
    title,
    description,
    address,
    urgency,
    availability,
    const DeepCollectionEquality().hash(_imageIds),
  );

  /// Create a copy of CreateServiceRequest
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @override
  @pragma('vm:prefer-inline')
  _$$CreateServiceRequestImplCopyWith<_$CreateServiceRequestImpl>
  get copyWith =>
      __$$CreateServiceRequestImplCopyWithImpl<_$CreateServiceRequestImpl>(
        this,
        _$identity,
      );

  @override
  Map<String, dynamic> toJson() {
    return _$$CreateServiceRequestImplToJson(this);
  }
}

abstract class _CreateServiceRequest implements CreateServiceRequest {
  const factory _CreateServiceRequest({
    required final String categoryId,
    required final String title,
    final String? description,
    required final Address address,
    final UrgencyLevel? urgency,
    final String? availability,
    final List<String> imageIds,
  }) = _$CreateServiceRequestImpl;

  factory _CreateServiceRequest.fromJson(Map<String, dynamic> json) =
      _$CreateServiceRequestImpl.fromJson;

  @override
  String get categoryId;
  @override
  String get title;
  @override
  String? get description;
  @override
  Address get address;
  @override
  UrgencyLevel? get urgency;
  @override
  String? get availability;
  @override
  List<String> get imageIds;

  /// Create a copy of CreateServiceRequest
  /// with the given fields replaced by the non-null parameter values.
  @override
  @JsonKey(includeFromJson: false, includeToJson: false)
  _$$CreateServiceRequestImplCopyWith<_$CreateServiceRequestImpl>
  get copyWith => throw _privateConstructorUsedError;
}
