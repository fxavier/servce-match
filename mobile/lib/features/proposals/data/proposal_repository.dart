import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/dio_provider.dart';
import '../../../core/network/generated/models/models.dart';

class ProposalRepository {
  ProposalRepository(this._ref);

  final Ref _ref;

  Future<ServiceRequest> getRequest(String requestId) =>
      _ref.read(serviMatchApiProvider).getRequest(requestId);

  Future<ProposalPage> listProposals(
    String requestId, {
    String? cursor,
  }) =>
      _ref.read(serviMatchApiProvider).listRequestProposals(
            requestId,
            cursor: cursor,
          );
}

final proposalRepositoryProvider = Provider<ProposalRepository>(
  (ref) => ProposalRepository(ref),
);
