// coverage:ignore-file
// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint
// ignore_for_file: unused_element, deprecated_member_use, deprecated_member_use_from_same_package, use_function_type_syntax_for_parameters, unnecessary_const, avoid_init_to_null, invalid_override_different_default_values_named, prefer_expression_function_bodies, annotate_overrides, invalid_annotation_target, unnecessary_question_mark

part of 'proposal.dart';

// **************************************************************************
// FreezedGenerator
// **************************************************************************

T _$identity<T>(T value) => value;

final _privateConstructorUsedError = UnsupportedError(
  'It seems like you constructed your class using `MyClass._()`. This constructor is only meant to be used by freezed and you are not supposed to need it nor use it.\nPlease check the documentation here for more information: https://github.com/rrousselGit/freezed#adding-getters-and-methods-to-our-models',
);

Proposal _$ProposalFromJson(Map<String, dynamic> json) {
  return _Proposal.fromJson(json);
}

/// @nodoc
mixin _$Proposal {
  String get id => throw _privateConstructorUsedError;
  String get requestId => throw _privateConstructorUsedError;
  String get providerId => throw _privateConstructorUsedError;
  ProviderSummary? get providerSummary => throw _privateConstructorUsedError;
  Money get price => throw _privateConstructorUsedError;
  String? get description => throw _privateConstructorUsedError;
  int? get leadTimeDays => throw _privateConstructorUsedError;
  DateTime? get validUntil => throw _privateConstructorUsedError;
  @JsonKey(unknownEnumValue: ProposalStatus.unknown)
  ProposalStatus get status => throw _privateConstructorUsedError;
  DateTime get createdAt => throw _privateConstructorUsedError;

  /// Serializes this Proposal to a JSON map.
  Map<String, dynamic> toJson() => throw _privateConstructorUsedError;

  /// Create a copy of Proposal
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  $ProposalCopyWith<Proposal> get copyWith =>
      throw _privateConstructorUsedError;
}

/// @nodoc
abstract class $ProposalCopyWith<$Res> {
  factory $ProposalCopyWith(Proposal value, $Res Function(Proposal) then) =
      _$ProposalCopyWithImpl<$Res, Proposal>;
  @useResult
  $Res call({
    String id,
    String requestId,
    String providerId,
    ProviderSummary? providerSummary,
    Money price,
    String? description,
    int? leadTimeDays,
    DateTime? validUntil,
    @JsonKey(unknownEnumValue: ProposalStatus.unknown) ProposalStatus status,
    DateTime createdAt,
  });

  $ProviderSummaryCopyWith<$Res>? get providerSummary;
  $MoneyCopyWith<$Res> get price;
}

/// @nodoc
class _$ProposalCopyWithImpl<$Res, $Val extends Proposal>
    implements $ProposalCopyWith<$Res> {
  _$ProposalCopyWithImpl(this._value, this._then);

  // ignore: unused_field
  final $Val _value;
  // ignore: unused_field
  final $Res Function($Val) _then;

  /// Create a copy of Proposal
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  @override
  $Res call({
    Object? id = null,
    Object? requestId = null,
    Object? providerId = null,
    Object? providerSummary = freezed,
    Object? price = null,
    Object? description = freezed,
    Object? leadTimeDays = freezed,
    Object? validUntil = freezed,
    Object? status = null,
    Object? createdAt = null,
  }) {
    return _then(
      _value.copyWith(
            id:
                null == id
                    ? _value.id
                    : id // ignore: cast_nullable_to_non_nullable
                        as String,
            requestId:
                null == requestId
                    ? _value.requestId
                    : requestId // ignore: cast_nullable_to_non_nullable
                        as String,
            providerId:
                null == providerId
                    ? _value.providerId
                    : providerId // ignore: cast_nullable_to_non_nullable
                        as String,
            providerSummary:
                freezed == providerSummary
                    ? _value.providerSummary
                    : providerSummary // ignore: cast_nullable_to_non_nullable
                        as ProviderSummary?,
            price:
                null == price
                    ? _value.price
                    : price // ignore: cast_nullable_to_non_nullable
                        as Money,
            description:
                freezed == description
                    ? _value.description
                    : description // ignore: cast_nullable_to_non_nullable
                        as String?,
            leadTimeDays:
                freezed == leadTimeDays
                    ? _value.leadTimeDays
                    : leadTimeDays // ignore: cast_nullable_to_non_nullable
                        as int?,
            validUntil:
                freezed == validUntil
                    ? _value.validUntil
                    : validUntil // ignore: cast_nullable_to_non_nullable
                        as DateTime?,
            status:
                null == status
                    ? _value.status
                    : status // ignore: cast_nullable_to_non_nullable
                        as ProposalStatus,
            createdAt:
                null == createdAt
                    ? _value.createdAt
                    : createdAt // ignore: cast_nullable_to_non_nullable
                        as DateTime,
          )
          as $Val,
    );
  }

  /// Create a copy of Proposal
  /// with the given fields replaced by the non-null parameter values.
  @override
  @pragma('vm:prefer-inline')
  $ProviderSummaryCopyWith<$Res>? get providerSummary {
    if (_value.providerSummary == null) {
      return null;
    }

    return $ProviderSummaryCopyWith<$Res>(_value.providerSummary!, (value) {
      return _then(_value.copyWith(providerSummary: value) as $Val);
    });
  }

  /// Create a copy of Proposal
  /// with the given fields replaced by the non-null parameter values.
  @override
  @pragma('vm:prefer-inline')
  $MoneyCopyWith<$Res> get price {
    return $MoneyCopyWith<$Res>(_value.price, (value) {
      return _then(_value.copyWith(price: value) as $Val);
    });
  }
}

/// @nodoc
abstract class _$$ProposalImplCopyWith<$Res>
    implements $ProposalCopyWith<$Res> {
  factory _$$ProposalImplCopyWith(
    _$ProposalImpl value,
    $Res Function(_$ProposalImpl) then,
  ) = __$$ProposalImplCopyWithImpl<$Res>;
  @override
  @useResult
  $Res call({
    String id,
    String requestId,
    String providerId,
    ProviderSummary? providerSummary,
    Money price,
    String? description,
    int? leadTimeDays,
    DateTime? validUntil,
    @JsonKey(unknownEnumValue: ProposalStatus.unknown) ProposalStatus status,
    DateTime createdAt,
  });

  @override
  $ProviderSummaryCopyWith<$Res>? get providerSummary;
  @override
  $MoneyCopyWith<$Res> get price;
}

/// @nodoc
class __$$ProposalImplCopyWithImpl<$Res>
    extends _$ProposalCopyWithImpl<$Res, _$ProposalImpl>
    implements _$$ProposalImplCopyWith<$Res> {
  __$$ProposalImplCopyWithImpl(
    _$ProposalImpl _value,
    $Res Function(_$ProposalImpl) _then,
  ) : super(_value, _then);

  /// Create a copy of Proposal
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  @override
  $Res call({
    Object? id = null,
    Object? requestId = null,
    Object? providerId = null,
    Object? providerSummary = freezed,
    Object? price = null,
    Object? description = freezed,
    Object? leadTimeDays = freezed,
    Object? validUntil = freezed,
    Object? status = null,
    Object? createdAt = null,
  }) {
    return _then(
      _$ProposalImpl(
        id:
            null == id
                ? _value.id
                : id // ignore: cast_nullable_to_non_nullable
                    as String,
        requestId:
            null == requestId
                ? _value.requestId
                : requestId // ignore: cast_nullable_to_non_nullable
                    as String,
        providerId:
            null == providerId
                ? _value.providerId
                : providerId // ignore: cast_nullable_to_non_nullable
                    as String,
        providerSummary:
            freezed == providerSummary
                ? _value.providerSummary
                : providerSummary // ignore: cast_nullable_to_non_nullable
                    as ProviderSummary?,
        price:
            null == price
                ? _value.price
                : price // ignore: cast_nullable_to_non_nullable
                    as Money,
        description:
            freezed == description
                ? _value.description
                : description // ignore: cast_nullable_to_non_nullable
                    as String?,
        leadTimeDays:
            freezed == leadTimeDays
                ? _value.leadTimeDays
                : leadTimeDays // ignore: cast_nullable_to_non_nullable
                    as int?,
        validUntil:
            freezed == validUntil
                ? _value.validUntil
                : validUntil // ignore: cast_nullable_to_non_nullable
                    as DateTime?,
        status:
            null == status
                ? _value.status
                : status // ignore: cast_nullable_to_non_nullable
                    as ProposalStatus,
        createdAt:
            null == createdAt
                ? _value.createdAt
                : createdAt // ignore: cast_nullable_to_non_nullable
                    as DateTime,
      ),
    );
  }
}

/// @nodoc
@JsonSerializable()
class _$ProposalImpl implements _Proposal {
  const _$ProposalImpl({
    required this.id,
    required this.requestId,
    required this.providerId,
    this.providerSummary,
    required this.price,
    this.description,
    this.leadTimeDays,
    this.validUntil,
    @JsonKey(unknownEnumValue: ProposalStatus.unknown) required this.status,
    required this.createdAt,
  });

  factory _$ProposalImpl.fromJson(Map<String, dynamic> json) =>
      _$$ProposalImplFromJson(json);

  @override
  final String id;
  @override
  final String requestId;
  @override
  final String providerId;
  @override
  final ProviderSummary? providerSummary;
  @override
  final Money price;
  @override
  final String? description;
  @override
  final int? leadTimeDays;
  @override
  final DateTime? validUntil;
  @override
  @JsonKey(unknownEnumValue: ProposalStatus.unknown)
  final ProposalStatus status;
  @override
  final DateTime createdAt;

  @override
  String toString() {
    return 'Proposal(id: $id, requestId: $requestId, providerId: $providerId, providerSummary: $providerSummary, price: $price, description: $description, leadTimeDays: $leadTimeDays, validUntil: $validUntil, status: $status, createdAt: $createdAt)';
  }

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is _$ProposalImpl &&
            (identical(other.id, id) || other.id == id) &&
            (identical(other.requestId, requestId) ||
                other.requestId == requestId) &&
            (identical(other.providerId, providerId) ||
                other.providerId == providerId) &&
            (identical(other.providerSummary, providerSummary) ||
                other.providerSummary == providerSummary) &&
            (identical(other.price, price) || other.price == price) &&
            (identical(other.description, description) ||
                other.description == description) &&
            (identical(other.leadTimeDays, leadTimeDays) ||
                other.leadTimeDays == leadTimeDays) &&
            (identical(other.validUntil, validUntil) ||
                other.validUntil == validUntil) &&
            (identical(other.status, status) || other.status == status) &&
            (identical(other.createdAt, createdAt) ||
                other.createdAt == createdAt));
  }

  @JsonKey(includeFromJson: false, includeToJson: false)
  @override
  int get hashCode => Object.hash(
    runtimeType,
    id,
    requestId,
    providerId,
    providerSummary,
    price,
    description,
    leadTimeDays,
    validUntil,
    status,
    createdAt,
  );

  /// Create a copy of Proposal
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @override
  @pragma('vm:prefer-inline')
  _$$ProposalImplCopyWith<_$ProposalImpl> get copyWith =>
      __$$ProposalImplCopyWithImpl<_$ProposalImpl>(this, _$identity);

  @override
  Map<String, dynamic> toJson() {
    return _$$ProposalImplToJson(this);
  }
}

abstract class _Proposal implements Proposal {
  const factory _Proposal({
    required final String id,
    required final String requestId,
    required final String providerId,
    final ProviderSummary? providerSummary,
    required final Money price,
    final String? description,
    final int? leadTimeDays,
    final DateTime? validUntil,
    @JsonKey(unknownEnumValue: ProposalStatus.unknown)
    required final ProposalStatus status,
    required final DateTime createdAt,
  }) = _$ProposalImpl;

  factory _Proposal.fromJson(Map<String, dynamic> json) =
      _$ProposalImpl.fromJson;

  @override
  String get id;
  @override
  String get requestId;
  @override
  String get providerId;
  @override
  ProviderSummary? get providerSummary;
  @override
  Money get price;
  @override
  String? get description;
  @override
  int? get leadTimeDays;
  @override
  DateTime? get validUntil;
  @override
  @JsonKey(unknownEnumValue: ProposalStatus.unknown)
  ProposalStatus get status;
  @override
  DateTime get createdAt;

  /// Create a copy of Proposal
  /// with the given fields replaced by the non-null parameter values.
  @override
  @JsonKey(includeFromJson: false, includeToJson: false)
  _$$ProposalImplCopyWith<_$ProposalImpl> get copyWith =>
      throw _privateConstructorUsedError;
}
