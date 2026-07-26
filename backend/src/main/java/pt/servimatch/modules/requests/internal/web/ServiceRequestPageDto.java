package pt.servimatch.modules.requests.internal.web;

import java.util.List;

public record ServiceRequestPageDto(List<ServiceRequestDto> items, PageMetaDto page) {
}
