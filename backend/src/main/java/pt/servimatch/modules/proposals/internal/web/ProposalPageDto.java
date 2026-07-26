package pt.servimatch.modules.proposals.internal.web;

import java.util.List;

public record ProposalPageDto(List<ProposalDto> items, PageMetaDto page) {
}
