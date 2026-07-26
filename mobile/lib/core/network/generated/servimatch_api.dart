import 'package:dio/dio.dart' hide Headers;
import 'package:retrofit/retrofit.dart';

import 'models/models.dart';

part 'servimatch_api.g.dart';

/// Cliente HTTP para o contrato `docs/api/openapi.yaml`.
///
/// A implementação (`servimatch_api.g.dart`) é gerada por `build_runner` —
/// ver mobile/lib/core/network/generated/README.md para o comando exato e
/// para a lista de operações ainda por cobrir nesta ronda.
@RestApi()
abstract class ServiMatchApi {
  factory ServiMatchApi(Dio dio, {String baseUrl}) = _ServiMatchApi;

  /// `getAppVersionStatus` — público, sem token.
  @GET('/v1/app/version-status')
  Future<VersionStatus> getAppVersionStatus(
    @Query('platform') String platform,
    @Query('appVersion') String appVersion,
  );

  /// `registerDeviceToken`.
  @POST('/v1/device-tokens')
  Future<void> registerDeviceToken(
    @Body() RegisterDeviceToken body, {
    @Header('Idempotency-Key') String? idempotencyKey,
  });

  /// `deleteDeviceToken`.
  @DELETE('/v1/device-tokens/{token}')
  Future<void> deleteDeviceToken(@Path('token') String token);

  /// `listCategories` — público, sem token.
  @GET('/v1/categories')
  Future<List<Category>> listCategories({
    @Query('parentId') String? parentId,
  });

  /// `createRequest` — role `CUSTOMER`.
  @POST('/v1/requests')
  Future<ServiceRequest> createRequest(
    @Body() CreateServiceRequest body, {
    @Header('Idempotency-Key') required String idempotencyKey,
  });

  /// `getRequest`.
  @GET('/v1/requests/{requestId}')
  Future<ServiceRequest> getRequest(@Path('requestId') String requestId);

  /// `publishRequest` — role `CUSTOMER` (dono).
  @POST('/v1/requests/{requestId}/publish')
  Future<ServiceRequest> publishRequest(@Path('requestId') String requestId);

  /// `listRequestProposals`.
  @GET('/v1/requests/{requestId}/proposals')
  Future<ProposalPage> listRequestProposals(
    @Path('requestId') String requestId, {
    @Query('limit') int? limit,
    @Query('cursor') String? cursor,
  });
}
