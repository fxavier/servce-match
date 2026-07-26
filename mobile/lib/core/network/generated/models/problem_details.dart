import 'package:freezed_annotation/freezed_annotation.dart';

part 'problem_details.freezed.dart';
part 'problem_details.g.dart';

/// Espelha o schema `ProblemDetails` (RFC 9457) de docs/api/openapi.yaml.
///
/// A UI ramifica pelo [type], nunca pela mensagem (ver skill
/// flutter-feature-slice).
@freezed
class ProblemDetails with _$ProblemDetails {
  const factory ProblemDetails({
    String? type,
    required String title,
    required int status,
    String? detail,
    String? instance,
    @Default(<ProblemFieldError>[]) List<ProblemFieldError> errors,
  }) = _ProblemDetails;

  factory ProblemDetails.fromJson(Map<String, dynamic> json) =>
      _$ProblemDetailsFromJson(json);
}

@freezed
class ProblemFieldError with _$ProblemFieldError {
  const factory ProblemFieldError({
    String? field,
    String? message,
  }) = _ProblemFieldError;

  factory ProblemFieldError.fromJson(Map<String, dynamic> json) =>
      _$ProblemFieldErrorFromJson(json);
}
