package pt.servimatch.modules.uploads.internal.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pt.servimatch.modules.uploads.UploadPurpose;

/** Espelha {@code docs/api/openapi.yaml#/components/schemas/CreateUpload}. */
public record CreateUploadRequest(
        @NotNull UploadPurpose purpose,
        @Size(max = 255) String fileName,
        @NotBlank String contentType,
        @NotNull @Min(1) Long contentLength
) {
}
