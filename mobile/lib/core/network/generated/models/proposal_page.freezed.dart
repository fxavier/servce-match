// coverage:ignore-file
// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint
// ignore_for_file: unused_element, deprecated_member_use, deprecated_member_use_from_same_package, use_function_type_syntax_for_parameters, unnecessary_const, avoid_init_to_null, invalid_override_different_default_values_named, prefer_expression_function_bodies, annotate_overrides, invalid_annotation_target, unnecessary_question_mark

part of 'proposal_page.dart';

// **************************************************************************
// FreezedGenerator
// **************************************************************************

T _$identity<T>(T value) => value;

final _privateConstructorUsedError = UnsupportedError(
  'It seems like you constructed your class using `MyClass._()`. This constructor is only meant to be used by freezed and you are not supposed to need it nor use it.\nPlease check the documentation here for more information: https://github.com/rrousselGit/freezed#adding-getters-and-methods-to-our-models',
);

ProposalPage _$ProposalPageFromJson(Map<String, dynamic> json) {
  return _ProposalPage.fromJson(json);
}

/// @nodoc
mixin _$ProposalPage {
  List<Proposal> get items => throw _privateConstructorUsedError;
  PageMeta get page => throw _privateConstructorUsedError;

  /// Serializes this ProposalPage to a JSON map.
  Map<String, dynamic> toJson() => throw _privateConstructorUsedError;

  /// Create a copy of ProposalPage
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  $ProposalPageCopyWith<ProposalPage> get copyWith =>
      throw _privateConstructorUsedError;
}

/// @nodoc
abstract class $ProposalPageCopyWith<$Res> {
  factory $ProposalPageCopyWith(
    ProposalPage value,
    $Res Function(ProposalPage) then,
  ) = _$ProposalPageCopyWithImpl<$Res, ProposalPage>;
  @useResult
  $Res call({List<Proposal> items, PageMeta page});

  $PageMetaCopyWith<$Res> get page;
}

/// @nodoc
class _$ProposalPageCopyWithImpl<$Res, $Val extends ProposalPage>
    implements $ProposalPageCopyWith<$Res> {
  _$ProposalPageCopyWithImpl(this._value, this._then);

  // ignore: unused_field
  final $Val _value;
  // ignore: unused_field
  final $Res Function($Val) _then;

  /// Create a copy of ProposalPage
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  @override
  $Res call({Object? items = null, Object? page = null}) {
    return _then(
      _value.copyWith(
            items:
                null == items
                    ? _value.items
                    : items // ignore: cast_nullable_to_non_nullable
                        as List<Proposal>,
            page:
                null == page
                    ? _value.page
                    : page // ignore: cast_nullable_to_non_nullable
                        as PageMeta,
          )
          as $Val,
    );
  }

  /// Create a copy of ProposalPage
  /// with the given fields replaced by the non-null parameter values.
  @override
  @pragma('vm:prefer-inline')
  $PageMetaCopyWith<$Res> get page {
    return $PageMetaCopyWith<$Res>(_value.page, (value) {
      return _then(_value.copyWith(page: value) as $Val);
    });
  }
}

/// @nodoc
abstract class _$$ProposalPageImplCopyWith<$Res>
    implements $ProposalPageCopyWith<$Res> {
  factory _$$ProposalPageImplCopyWith(
    _$ProposalPageImpl value,
    $Res Function(_$ProposalPageImpl) then,
  ) = __$$ProposalPageImplCopyWithImpl<$Res>;
  @override
  @useResult
  $Res call({List<Proposal> items, PageMeta page});

  @override
  $PageMetaCopyWith<$Res> get page;
}

/// @nodoc
class __$$ProposalPageImplCopyWithImpl<$Res>
    extends _$ProposalPageCopyWithImpl<$Res, _$ProposalPageImpl>
    implements _$$ProposalPageImplCopyWith<$Res> {
  __$$ProposalPageImplCopyWithImpl(
    _$ProposalPageImpl _value,
    $Res Function(_$ProposalPageImpl) _then,
  ) : super(_value, _then);

  /// Create a copy of ProposalPage
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  @override
  $Res call({Object? items = null, Object? page = null}) {
    return _then(
      _$ProposalPageImpl(
        items:
            null == items
                ? _value._items
                : items // ignore: cast_nullable_to_non_nullable
                    as List<Proposal>,
        page:
            null == page
                ? _value.page
                : page // ignore: cast_nullable_to_non_nullable
                    as PageMeta,
      ),
    );
  }
}

/// @nodoc
@JsonSerializable()
class _$ProposalPageImpl implements _ProposalPage {
  const _$ProposalPageImpl({
    required final List<Proposal> items,
    required this.page,
  }) : _items = items;

  factory _$ProposalPageImpl.fromJson(Map<String, dynamic> json) =>
      _$$ProposalPageImplFromJson(json);

  final List<Proposal> _items;
  @override
  List<Proposal> get items {
    if (_items is EqualUnmodifiableListView) return _items;
    // ignore: implicit_dynamic_type
    return EqualUnmodifiableListView(_items);
  }

  @override
  final PageMeta page;

  @override
  String toString() {
    return 'ProposalPage(items: $items, page: $page)';
  }

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is _$ProposalPageImpl &&
            const DeepCollectionEquality().equals(other._items, _items) &&
            (identical(other.page, page) || other.page == page));
  }

  @JsonKey(includeFromJson: false, includeToJson: false)
  @override
  int get hashCode => Object.hash(
    runtimeType,
    const DeepCollectionEquality().hash(_items),
    page,
  );

  /// Create a copy of ProposalPage
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @override
  @pragma('vm:prefer-inline')
  _$$ProposalPageImplCopyWith<_$ProposalPageImpl> get copyWith =>
      __$$ProposalPageImplCopyWithImpl<_$ProposalPageImpl>(this, _$identity);

  @override
  Map<String, dynamic> toJson() {
    return _$$ProposalPageImplToJson(this);
  }
}

abstract class _ProposalPage implements ProposalPage {
  const factory _ProposalPage({
    required final List<Proposal> items,
    required final PageMeta page,
  }) = _$ProposalPageImpl;

  factory _ProposalPage.fromJson(Map<String, dynamic> json) =
      _$ProposalPageImpl.fromJson;

  @override
  List<Proposal> get items;
  @override
  PageMeta get page;

  /// Create a copy of ProposalPage
  /// with the given fields replaced by the non-null parameter values.
  @override
  @JsonKey(includeFromJson: false, includeToJson: false)
  _$$ProposalPageImplCopyWith<_$ProposalPageImpl> get copyWith =>
      throw _privateConstructorUsedError;
}
