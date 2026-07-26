// coverage:ignore-file
// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint
// ignore_for_file: unused_element, deprecated_member_use, deprecated_member_use_from_same_package, use_function_type_syntax_for_parameters, unnecessary_const, avoid_init_to_null, invalid_override_different_default_values_named, prefer_expression_function_bodies, annotate_overrides, invalid_annotation_target, unnecessary_question_mark

part of 'service_request.dart';

// **************************************************************************
// FreezedGenerator
// **************************************************************************

T _$identity<T>(T value) => value;

final _privateConstructorUsedError = UnsupportedError(
  'It seems like you constructed your class using `MyClass._()`. This constructor is only meant to be used by freezed and you are not supposed to need it nor use it.\nPlease check the documentation here for more information: https://github.com/rrousselGit/freezed#adding-getters-and-methods-to-our-models',
);

ServiceRequest _$ServiceRequestFromJson(Map<String, dynamic> json) {
  return _ServiceRequest.fromJson(json);
}

/// @nodoc
mixin _$ServiceRequest {
  String get id => throw _privateConstructorUsedError;
  String get customerId => throw _privateConstructorUsedError;
  Category? get category => throw _privateConstructorUsedError;
  String get title => throw _privateConstructorUsedError;
  String? get description => throw _privateConstructorUsedError;
  Address? get address => throw _privateConstructorUsedError;
  UrgencyLevel? get urgency => throw _privateConstructorUsedError;
  String? get availability => throw _privateConstructorUsedError;
  @JsonKey(unknownEnumValue: RequestStatus.unknown)
  RequestStatus get status => throw _privateConstructorUsedError;
  List<ImageRef> get images => throw _privateConstructorUsedError;
  int? get proposalCount => throw _privateConstructorUsedError;
  DateTime get createdAt => throw _privateConstructorUsedError;
  DateTime? get publishedAt => throw _privateConstructorUsedError;

  /// Serializes this ServiceRequest to a JSON map.
  Map<String, dynamic> toJson() => throw _privateConstructorUsedError;

  /// Create a copy of ServiceRequest
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  $ServiceRequestCopyWith<ServiceRequest> get copyWith =>
      throw _privateConstructorUsedError;
}

/// @nodoc
abstract class $ServiceRequestCopyWith<$Res> {
  factory $ServiceRequestCopyWith(
    ServiceRequest value,
    $Res Function(ServiceRequest) then,
  ) = _$ServiceRequestCopyWithImpl<$Res, ServiceRequest>;
  @useResult
  $Res call({
    String id,
    String customerId,
    Category? category,
    String title,
    String? description,
    Address? address,
    UrgencyLevel? urgency,
    String? availability,
    @JsonKey(unknownEnumValue: RequestStatus.unknown) RequestStatus status,
    List<ImageRef> images,
    int? proposalCount,
    DateTime createdAt,
    DateTime? publishedAt,
  });

  $CategoryCopyWith<$Res>? get category;
  $AddressCopyWith<$Res>? get address;
}

/// @nodoc
class _$ServiceRequestCopyWithImpl<$Res, $Val extends ServiceRequest>
    implements $ServiceRequestCopyWith<$Res> {
  _$ServiceRequestCopyWithImpl(this._value, this._then);

  // ignore: unused_field
  final $Val _value;
  // ignore: unused_field
  final $Res Function($Val) _then;

  /// Create a copy of ServiceRequest
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  @override
  $Res call({
    Object? id = null,
    Object? customerId = null,
    Object? category = freezed,
    Object? title = null,
    Object? description = freezed,
    Object? address = freezed,
    Object? urgency = freezed,
    Object? availability = freezed,
    Object? status = null,
    Object? images = null,
    Object? proposalCount = freezed,
    Object? createdAt = null,
    Object? publishedAt = freezed,
  }) {
    return _then(
      _value.copyWith(
            id:
                null == id
                    ? _value.id
                    : id // ignore: cast_nullable_to_non_nullable
                        as String,
            customerId:
                null == customerId
                    ? _value.customerId
                    : customerId // ignore: cast_nullable_to_non_nullable
                        as String,
            category:
                freezed == category
                    ? _value.category
                    : category // ignore: cast_nullable_to_non_nullable
                        as Category?,
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
                freezed == address
                    ? _value.address
                    : address // ignore: cast_nullable_to_non_nullable
                        as Address?,
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
            status:
                null == status
                    ? _value.status
                    : status // ignore: cast_nullable_to_non_nullable
                        as RequestStatus,
            images:
                null == images
                    ? _value.images
                    : images // ignore: cast_nullable_to_non_nullable
                        as List<ImageRef>,
            proposalCount:
                freezed == proposalCount
                    ? _value.proposalCount
                    : proposalCount // ignore: cast_nullable_to_non_nullable
                        as int?,
            createdAt:
                null == createdAt
                    ? _value.createdAt
                    : createdAt // ignore: cast_nullable_to_non_nullable
                        as DateTime,
            publishedAt:
                freezed == publishedAt
                    ? _value.publishedAt
                    : publishedAt // ignore: cast_nullable_to_non_nullable
                        as DateTime?,
          )
          as $Val,
    );
  }

  /// Create a copy of ServiceRequest
  /// with the given fields replaced by the non-null parameter values.
  @override
  @pragma('vm:prefer-inline')
  $CategoryCopyWith<$Res>? get category {
    if (_value.category == null) {
      return null;
    }

    return $CategoryCopyWith<$Res>(_value.category!, (value) {
      return _then(_value.copyWith(category: value) as $Val);
    });
  }

  /// Create a copy of ServiceRequest
  /// with the given fields replaced by the non-null parameter values.
  @override
  @pragma('vm:prefer-inline')
  $AddressCopyWith<$Res>? get address {
    if (_value.address == null) {
      return null;
    }

    return $AddressCopyWith<$Res>(_value.address!, (value) {
      return _then(_value.copyWith(address: value) as $Val);
    });
  }
}

/// @nodoc
abstract class _$$ServiceRequestImplCopyWith<$Res>
    implements $ServiceRequestCopyWith<$Res> {
  factory _$$ServiceRequestImplCopyWith(
    _$ServiceRequestImpl value,
    $Res Function(_$ServiceRequestImpl) then,
  ) = __$$ServiceRequestImplCopyWithImpl<$Res>;
  @override
  @useResult
  $Res call({
    String id,
    String customerId,
    Category? category,
    String title,
    String? description,
    Address? address,
    UrgencyLevel? urgency,
    String? availability,
    @JsonKey(unknownEnumValue: RequestStatus.unknown) RequestStatus status,
    List<ImageRef> images,
    int? proposalCount,
    DateTime createdAt,
    DateTime? publishedAt,
  });

  @override
  $CategoryCopyWith<$Res>? get category;
  @override
  $AddressCopyWith<$Res>? get address;
}

/// @nodoc
class __$$ServiceRequestImplCopyWithImpl<$Res>
    extends _$ServiceRequestCopyWithImpl<$Res, _$ServiceRequestImpl>
    implements _$$ServiceRequestImplCopyWith<$Res> {
  __$$ServiceRequestImplCopyWithImpl(
    _$ServiceRequestImpl _value,
    $Res Function(_$ServiceRequestImpl) _then,
  ) : super(_value, _then);

  /// Create a copy of ServiceRequest
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  @override
  $Res call({
    Object? id = null,
    Object? customerId = null,
    Object? category = freezed,
    Object? title = null,
    Object? description = freezed,
    Object? address = freezed,
    Object? urgency = freezed,
    Object? availability = freezed,
    Object? status = null,
    Object? images = null,
    Object? proposalCount = freezed,
    Object? createdAt = null,
    Object? publishedAt = freezed,
  }) {
    return _then(
      _$ServiceRequestImpl(
        id:
            null == id
                ? _value.id
                : id // ignore: cast_nullable_to_non_nullable
                    as String,
        customerId:
            null == customerId
                ? _value.customerId
                : customerId // ignore: cast_nullable_to_non_nullable
                    as String,
        category:
            freezed == category
                ? _value.category
                : category // ignore: cast_nullable_to_non_nullable
                    as Category?,
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
            freezed == address
                ? _value.address
                : address // ignore: cast_nullable_to_non_nullable
                    as Address?,
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
        status:
            null == status
                ? _value.status
                : status // ignore: cast_nullable_to_non_nullable
                    as RequestStatus,
        images:
            null == images
                ? _value._images
                : images // ignore: cast_nullable_to_non_nullable
                    as List<ImageRef>,
        proposalCount:
            freezed == proposalCount
                ? _value.proposalCount
                : proposalCount // ignore: cast_nullable_to_non_nullable
                    as int?,
        createdAt:
            null == createdAt
                ? _value.createdAt
                : createdAt // ignore: cast_nullable_to_non_nullable
                    as DateTime,
        publishedAt:
            freezed == publishedAt
                ? _value.publishedAt
                : publishedAt // ignore: cast_nullable_to_non_nullable
                    as DateTime?,
      ),
    );
  }
}

/// @nodoc
@JsonSerializable()
class _$ServiceRequestImpl implements _ServiceRequest {
  const _$ServiceRequestImpl({
    required this.id,
    required this.customerId,
    this.category,
    required this.title,
    this.description,
    this.address,
    this.urgency,
    this.availability,
    @JsonKey(unknownEnumValue: RequestStatus.unknown) required this.status,
    final List<ImageRef> images = const <ImageRef>[],
    this.proposalCount,
    required this.createdAt,
    this.publishedAt,
  }) : _images = images;

  factory _$ServiceRequestImpl.fromJson(Map<String, dynamic> json) =>
      _$$ServiceRequestImplFromJson(json);

  @override
  final String id;
  @override
  final String customerId;
  @override
  final Category? category;
  @override
  final String title;
  @override
  final String? description;
  @override
  final Address? address;
  @override
  final UrgencyLevel? urgency;
  @override
  final String? availability;
  @override
  @JsonKey(unknownEnumValue: RequestStatus.unknown)
  final RequestStatus status;
  final List<ImageRef> _images;
  @override
  @JsonKey()
  List<ImageRef> get images {
    if (_images is EqualUnmodifiableListView) return _images;
    // ignore: implicit_dynamic_type
    return EqualUnmodifiableListView(_images);
  }

  @override
  final int? proposalCount;
  @override
  final DateTime createdAt;
  @override
  final DateTime? publishedAt;

  @override
  String toString() {
    return 'ServiceRequest(id: $id, customerId: $customerId, category: $category, title: $title, description: $description, address: $address, urgency: $urgency, availability: $availability, status: $status, images: $images, proposalCount: $proposalCount, createdAt: $createdAt, publishedAt: $publishedAt)';
  }

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is _$ServiceRequestImpl &&
            (identical(other.id, id) || other.id == id) &&
            (identical(other.customerId, customerId) ||
                other.customerId == customerId) &&
            (identical(other.category, category) ||
                other.category == category) &&
            (identical(other.title, title) || other.title == title) &&
            (identical(other.description, description) ||
                other.description == description) &&
            (identical(other.address, address) || other.address == address) &&
            (identical(other.urgency, urgency) || other.urgency == urgency) &&
            (identical(other.availability, availability) ||
                other.availability == availability) &&
            (identical(other.status, status) || other.status == status) &&
            const DeepCollectionEquality().equals(other._images, _images) &&
            (identical(other.proposalCount, proposalCount) ||
                other.proposalCount == proposalCount) &&
            (identical(other.createdAt, createdAt) ||
                other.createdAt == createdAt) &&
            (identical(other.publishedAt, publishedAt) ||
                other.publishedAt == publishedAt));
  }

  @JsonKey(includeFromJson: false, includeToJson: false)
  @override
  int get hashCode => Object.hash(
    runtimeType,
    id,
    customerId,
    category,
    title,
    description,
    address,
    urgency,
    availability,
    status,
    const DeepCollectionEquality().hash(_images),
    proposalCount,
    createdAt,
    publishedAt,
  );

  /// Create a copy of ServiceRequest
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @override
  @pragma('vm:prefer-inline')
  _$$ServiceRequestImplCopyWith<_$ServiceRequestImpl> get copyWith =>
      __$$ServiceRequestImplCopyWithImpl<_$ServiceRequestImpl>(
        this,
        _$identity,
      );

  @override
  Map<String, dynamic> toJson() {
    return _$$ServiceRequestImplToJson(this);
  }
}

abstract class _ServiceRequest implements ServiceRequest {
  const factory _ServiceRequest({
    required final String id,
    required final String customerId,
    final Category? category,
    required final String title,
    final String? description,
    final Address? address,
    final UrgencyLevel? urgency,
    final String? availability,
    @JsonKey(unknownEnumValue: RequestStatus.unknown)
    required final RequestStatus status,
    final List<ImageRef> images,
    final int? proposalCount,
    required final DateTime createdAt,
    final DateTime? publishedAt,
  }) = _$ServiceRequestImpl;

  factory _ServiceRequest.fromJson(Map<String, dynamic> json) =
      _$ServiceRequestImpl.fromJson;

  @override
  String get id;
  @override
  String get customerId;
  @override
  Category? get category;
  @override
  String get title;
  @override
  String? get description;
  @override
  Address? get address;
  @override
  UrgencyLevel? get urgency;
  @override
  String? get availability;
  @override
  @JsonKey(unknownEnumValue: RequestStatus.unknown)
  RequestStatus get status;
  @override
  List<ImageRef> get images;
  @override
  int? get proposalCount;
  @override
  DateTime get createdAt;
  @override
  DateTime? get publishedAt;

  /// Create a copy of ServiceRequest
  /// with the given fields replaced by the non-null parameter values.
  @override
  @JsonKey(includeFromJson: false, includeToJson: false)
  _$$ServiceRequestImplCopyWith<_$ServiceRequestImpl> get copyWith =>
      throw _privateConstructorUsedError;
}
