import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/core/network/app_exception.dart';
import 'package:mobile/core/network/dio_provider.dart';
import 'package:mobile/core/network/generated/models/models.dart';
import 'package:mobile/core/network/generated/servimatch_api.dart';
import 'package:mobile/features/proposals/presentation/proposals_providers.dart';
import 'package:mocktail/mocktail.dart';

class _MockServiMatchApi extends Mock implements ServiMatchApi {}

Proposal _proposal(String id) => Proposal(
      id: id,
      requestId: 'req-1',
      providerId: 'prov-1',
      price: const Money(amountCents: 4500, currency: 'EUR'),
      status: ProposalStatus.sent,
      createdAt: DateTime.utc(2026, 1, 2),
    );

void main() {
  late _MockServiMatchApi api;
  late ProviderContainer container;

  setUp(() {
    api = _MockServiMatchApi();
    container = ProviderContainer(
      overrides: [serviMatchApiProvider.overrideWithValue(api)],
    );
    addTearDown(container.dispose);
  });

  test(
    'lista propostas devolvidas pelo servidor (caminho principal)',
    () async {
      when(() => api.listRequestProposals('req-1', cursor: null)).thenAnswer(
        (_) async => ProposalPage(
          items: [_proposal('p1'), _proposal('p2')],
          page: const PageMeta(),
        ),
      );

      final page = await container.read(proposalsProvider('req-1').future);

      expect(page.items, hasLength(2));
      expect(page.items.first.id, 'p1');
    },
  );

  test(
    'propaga AppException quando o servidor falha (caso de erro)',
    () async {
      when(() => api.listRequestProposals('req-1', cursor: null))
          .thenThrow(const OfflineException());

      await expectLater(
        container.read(proposalsProvider('req-1').future),
        throwsA(isA<OfflineException>()),
      );
    },
  );
}
