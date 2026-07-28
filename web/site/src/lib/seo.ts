/** Helpers de JSON-LD (§10 — SEO). Devolvem objetos prontos a serializar num `<script type="application/ld+json">`. */

export function localBusinessJsonLd(input: {
  name: string;
  description: string;
  url: string;
  areaServed?: string[];
}): Record<string, unknown> {
  return {
    '@context': 'https://schema.org',
    '@type': 'LocalBusiness',
    name: input.name,
    description: input.description,
    url: input.url,
    areaServed: input.areaServed,
  };
}

export function serviceJsonLd(input: {
  name: string;
  description: string;
  areaServed?: string;
  priceRangeFrom?: number;
  currency?: string;
}): Record<string, unknown> {
  return {
    '@context': 'https://schema.org',
    '@type': 'Service',
    name: input.name,
    description: input.description,
    areaServed: input.areaServed,
    offers: input.priceRangeFrom
      ? {
          '@type': 'Offer',
          priceCurrency: input.currency ?? 'EUR',
          price: (input.priceRangeFrom / 100).toFixed(2),
        }
      : undefined,
  };
}

export function aggregateRatingJsonLd(input: {
  itemName: string;
  ratingValue: number;
  reviewCount: number;
}): Record<string, unknown> {
  return {
    '@context': 'https://schema.org',
    '@type': 'Product',
    name: input.itemName,
    aggregateRating: {
      '@type': 'AggregateRating',
      ratingValue: input.ratingValue.toFixed(1),
      reviewCount: input.reviewCount,
    },
  };
}
