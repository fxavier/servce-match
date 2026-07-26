import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../../../core/network/generated/models/models.dart';
import '../../../l10n/generated/app_localizations.dart';
import '../../../shared/widgets/async_value_view.dart';
import '../../../shared/widgets/offline_banner.dart';
import 'proposals_providers.dart';

/// `GET /v1/requests/{requestId}/proposals` — último passo do fluxo
/// mínimo desta ronda: autenticar → criar pedido → **ver propostas**.
///
/// Aceitar proposta (`POST /v1/proposals/{id}/accept`) fica para a ronda
/// seguinte — aqui só a listagem, com os quatro estados obrigatórios
/// (carregamento, dados, vazio, erro/offline).
class ProposalsScreen extends ConsumerWidget {
  const ProposalsScreen({required this.requestId, super.key});

  final String requestId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final proposals = ref.watch(proposalsProvider(requestId));
    final requestDetail = ref.watch(requestDetailProvider(requestId));

    return Scaffold(
      appBar: AppBar(
        title: Text(
          requestDetail.valueOrNull?.title ?? l10n.proposalsTitle,
        ),
      ),
      body: Column(
        children: [
          const OfflineBanner(),
          Expanded(
            child: RefreshIndicator(
              onRefresh: () async {
                ref.invalidate(proposalsProvider(requestId));
                ref.invalidate(requestDetailProvider(requestId));
                await ref.read(proposalsProvider(requestId).future);
              },
              child: AsyncValueView<ProposalPage>(
                value: proposals,
                onRetry: () => ref.invalidate(proposalsProvider(requestId)),
                isEmpty: (page) => page.items.isEmpty,
                emptyBuilder: (context) => ListView(
                  // ListView para o RefreshIndicator funcionar mesmo vazio.
                  children: [
                    Padding(
                      padding: const EdgeInsets.only(top: 96),
                      child: Center(
                        child: Column(
                          children: [
                            const Icon(Icons.inbox_outlined, size: 48),
                            const SizedBox(height: 12),
                            Text(l10n.proposalsEmptyMessage),
                          ],
                        ),
                      ),
                    ),
                  ],
                ),
                dataBuilder: (context, page) => ListView.separated(
                  padding: const EdgeInsets.all(16),
                  itemCount: page.items.length + 1,
                  separatorBuilder: (_, _) => const SizedBox(height: 8),
                  itemBuilder: (context, index) {
                    if (index == 0) {
                      return Padding(
                        padding: const EdgeInsets.only(bottom: 8),
                        child: Text(
                          l10n.proposalsCountLabel(page.items.length),
                          style: Theme.of(context).textTheme.titleMedium,
                        ),
                      );
                    }
                    return _ProposalCard(proposal: page.items[index - 1]);
                  },
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _ProposalCard extends StatelessWidget {
  const _ProposalCard({required this.proposal});

  final Proposal proposal;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final price = NumberFormat.simpleCurrency(name: proposal.price.currency)
        .format(proposal.price.amountCents / 100);
    final providerName =
        proposal.providerSummary?.displayName ?? l10n.commonUnknown;

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              providerName,
              style: Theme.of(context).textTheme.titleMedium,
            ),
            const SizedBox(height: 4),
            Text('${l10n.proposalPriceLabel}: $price'),
            if (proposal.leadTimeDays != null) ...[
              const SizedBox(height: 4),
              Text(l10n.proposalLeadTimeLabel(proposal.leadTimeDays!)),
            ],
            if (proposal.description != null &&
                proposal.description!.isNotEmpty) ...[
              const SizedBox(height: 8),
              Text(proposal.description!),
            ],
          ],
        ),
      ),
    );
  }
}
