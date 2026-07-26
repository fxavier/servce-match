import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/generated/models/models.dart';
import '../data/proposal_repository.dart';

final requestDetailProvider =
    FutureProvider.autoDispose.family<ServiceRequest, String>(
  (ref, requestId) => ref.watch(proposalRepositoryProvider).getRequest(requestId),
);

final proposalsProvider =
    FutureProvider.autoDispose.family<ProposalPage, String>(
  (ref, requestId) =>
      ref.watch(proposalRepositoryProvider).listProposals(requestId),
);
