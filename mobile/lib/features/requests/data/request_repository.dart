import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:uuid/uuid.dart';

import '../../../core/network/dio_provider.dart';
import '../../../core/network/generated/models/models.dart';

/// Sobre o cliente gerado (`core/network/generated`) — não reimplementa
/// chamadas HTTP, só compõe as operações do contrato necessárias ao fluxo
/// "criar pedido" (skill flutter-feature-slice).
class RequestRepository {
  RequestRepository(this._ref);

  final Ref _ref;

  Future<List<Category>> listCategories() =>
      _ref.read(serviMatchApiProvider).listCategories();

  Future<ServiceRequest> getRequest(String requestId) =>
      _ref.read(serviMatchApiProvider).getRequest(requestId);

  /// Cria o pedido em `DRAFT` e publica-o de imediato — no MVP o Cliente
  /// não gere rascunhos, só pedidos publicados (dispara o *matching*).
  /// `createRequest` usa uma `Idempotency-Key` própria por submissão para
  /// que reenvios (ex. um duplo toque, ou um retry de rede) não criem
  /// pedidos duplicados.
  Future<ServiceRequest> createAndPublish(CreateServiceRequest input) async {
    final api = _ref.read(serviMatchApiProvider);
    final draft = await api.createRequest(
      input,
      idempotencyKey: const Uuid().v4(),
    );
    return api.publishRequest(draft.id);
  }
}

final requestRepositoryProvider = Provider<RequestRepository>(
  (ref) => RequestRepository(ref),
);

final categoriesProvider = FutureProvider.autoDispose<List<Category>>(
  (ref) => ref.watch(requestRepositoryProvider).listCategories(),
);
