import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/core/network/dio_provider.dart';
import 'package:mobile/core/network/generated/models/models.dart';
import 'package:mobile/core/network/generated/servimatch_api.dart';
import 'package:mobile/features/requests/presentation/create_request_controller.dart';
import 'package:mocktail/mocktail.dart';

class _MockServiMatchApi extends Mock implements ServiMatchApi {}

class _FakeCreateServiceRequest extends Fake implements CreateServiceRequest {}

CreateServiceRequest _input() => const CreateServiceRequest(
      categoryId: 'cat-1',
      title: 'Fuga de água na cozinha',
      address: Address(city: 'Lisboa', postalCode: '1000-001'),
    );

ServiceRequest _draft() => ServiceRequest(
      id: 'req-1',
      customerId: 'cust-1',
      title: 'Fuga de água na cozinha',
      status: RequestStatus.draft,
      createdAt: DateTime.utc(2026, 1, 1),
    );

ServiceRequest _published() => ServiceRequest(
      id: 'req-1',
      customerId: 'cust-1',
      title: 'Fuga de água na cozinha',
      status: RequestStatus.published,
      createdAt: DateTime.utc(2026, 1, 1),
      publishedAt: DateTime.utc(2026, 1, 1),
    );

void main() {
  setUpAll(() {
    registerFallbackValue(_FakeCreateServiceRequest());
  });

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
    'submit: cria e publica o pedido -> AsyncData (caminho principal)',
    () async {
      when(
        () => api.createRequest(
          any(),
          idempotencyKey: any(named: 'idempotencyKey'),
        ),
      ).thenAnswer((_) async => _draft());
      when(() => api.publishRequest('req-1'))
          .thenAnswer((_) async => _published());

      final controller =
          container.read(createRequestControllerProvider.notifier);
      await controller.submit(_input());

      final state = container.read(createRequestControllerProvider);
      expect(state.hasValue, isTrue);
      expect(state.value?.status, RequestStatus.published);
      verify(() => api.publishRequest('req-1')).called(1);
    },
  );

  test(
    'submit: erro do servidor ao criar -> AsyncError (caso de erro)',
    () async {
      when(
        () => api.createRequest(
          any(),
          idempotencyKey: any(named: 'idempotencyKey'),
        ),
      ).thenThrow(Exception('falha simulada do servidor'));

      final controller =
          container.read(createRequestControllerProvider.notifier);
      await controller.submit(_input());

      final state = container.read(createRequestControllerProvider);
      expect(state.hasError, isTrue);
      verifyNever(() => api.publishRequest(any()));
    },
  );
}
