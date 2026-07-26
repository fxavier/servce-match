import 'package:freezed_annotation/freezed_annotation.dart';

import 'address.dart';
import 'category.dart';
import 'image_ref.dart';
import 'request_status.dart';
import 'urgency_level.dart';

part 'service_request.freezed.dart';
part 'service_request.g.dart';

/// Espelha o schema `ServiceRequest` de docs/api/openapi.yaml.
@freezed
class ServiceRequest with _$ServiceRequest {
  const factory ServiceRequest({
    required String id,
    required String customerId,
    Category? category,
    required String title,
    String? description,
    Address? address,
    UrgencyLevel? urgency,
    String? availability,
    @JsonKey(unknownEnumValue: RequestStatus.unknown)
    required RequestStatus status,
    @Default(<ImageRef>[]) List<ImageRef> images,
    int? proposalCount,
    required DateTime createdAt,
    DateTime? publishedAt,
  }) = _ServiceRequest;

  factory ServiceRequest.fromJson(Map<String, dynamic> json) =>
      _$ServiceRequestFromJson(json);
}
