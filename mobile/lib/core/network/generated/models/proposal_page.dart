import 'package:freezed_annotation/freezed_annotation.dart';

import 'page_meta.dart';
import 'proposal.dart';

part 'proposal_page.freezed.dart';
part 'proposal_page.g.dart';

/// Espelha o schema `ProposalPage` de docs/api/openapi.yaml.
@freezed
class ProposalPage with _$ProposalPage {
  const factory ProposalPage({
    required List<Proposal> items,
    required PageMeta page,
  }) = _ProposalPage;

  factory ProposalPage.fromJson(Map<String, dynamic> json) =>
      _$ProposalPageFromJson(json);
}
