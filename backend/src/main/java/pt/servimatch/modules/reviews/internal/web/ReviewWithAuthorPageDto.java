package pt.servimatch.modules.reviews.internal.web;

import java.util.List;

public record ReviewWithAuthorPageDto(List<ReviewWithAuthorDto> items, PageMetaDto page) {
}
