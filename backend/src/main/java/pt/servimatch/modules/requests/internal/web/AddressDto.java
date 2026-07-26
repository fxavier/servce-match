package pt.servimatch.modules.requests.internal.web;

import jakarta.validation.Valid;

public record AddressDto(
        String line1,
        String line2,
        String postalCode,
        String city,
        String regionCode,
        String country,
        @Valid GeoPointDto location) {
}
