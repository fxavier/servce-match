// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'problem_details.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

_$ProblemDetailsImpl _$$ProblemDetailsImplFromJson(Map<String, dynamic> json) =>
    _$ProblemDetailsImpl(
      type: json['type'] as String?,
      title: json['title'] as String,
      status: (json['status'] as num).toInt(),
      detail: json['detail'] as String?,
      instance: json['instance'] as String?,
      errors:
          (json['errors'] as List<dynamic>?)
              ?.map(
                (e) => ProblemFieldError.fromJson(e as Map<String, dynamic>),
              )
              .toList() ??
          const <ProblemFieldError>[],
    );

Map<String, dynamic> _$$ProblemDetailsImplToJson(
  _$ProblemDetailsImpl instance,
) => <String, dynamic>{
  'type': instance.type,
  'title': instance.title,
  'status': instance.status,
  'detail': instance.detail,
  'instance': instance.instance,
  'errors': instance.errors,
};

_$ProblemFieldErrorImpl _$$ProblemFieldErrorImplFromJson(
  Map<String, dynamic> json,
) => _$ProblemFieldErrorImpl(
  field: json['field'] as String?,
  message: json['message'] as String?,
);

Map<String, dynamic> _$$ProblemFieldErrorImplToJson(
  _$ProblemFieldErrorImpl instance,
) => <String, dynamic>{'field': instance.field, 'message': instance.message};
