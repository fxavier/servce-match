import 'package:freezed_annotation/freezed_annotation.dart';

import 'address.dart';
import 'urgency_level.dart';

part 'create_service_request.freezed.dart';
part 'create_service_request.g.dart';

/// Espelha o schema `CreateServiceRequest` (body de `POST /v1/requests`).
@freezed
class CreateServiceRequest with _$CreateServiceRequest {
  const factory CreateServiceRequest({
    required String categoryId,
    required String title,
    String? description,
    required Address address,
    UrgencyLevel? urgency,
    String? availability,
    @Default(<String>[]) List<String> imageIds,
  }) = _CreateServiceRequest;

  factory CreateServiceRequest.fromJson(Map<String, dynamic> json) =>
      _$CreateServiceRequestFromJson(json);
}
