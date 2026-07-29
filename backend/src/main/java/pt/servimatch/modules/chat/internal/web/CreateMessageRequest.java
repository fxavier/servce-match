package pt.servimatch.modules.chat.internal.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateMessageRequest(
        @NotBlank @Size(max = 4000) String body,
        List<UUID> attachmentIds) {
}
