// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'proposal_page.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

_$ProposalPageImpl _$$ProposalPageImplFromJson(Map<String, dynamic> json) =>
    _$ProposalPageImpl(
      items:
          (json['items'] as List<dynamic>)
              .map((e) => Proposal.fromJson(e as Map<String, dynamic>))
              .toList(),
      page: PageMeta.fromJson(json['page'] as Map<String, dynamic>),
    );

Map<String, dynamic> _$$ProposalPageImplToJson(_$ProposalPageImpl instance) =>
    <String, dynamic>{'items': instance.items, 'page': instance.page};
