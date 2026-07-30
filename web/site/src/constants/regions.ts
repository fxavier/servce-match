import type { GeoPoint } from '../services/types';

export interface Region {
  code: string;
  label: string;
  location: GeoPoint;
}

/**
 * Concelhos reais com coordenadas aproximadas (WGS84). Isto **não** é dado
 * do servidor — é referência geográfica estática (como um enum), usada
 * pelo seletor de concelho. O contrato não expõe um endpoint
 * `GET /v1/regions` (não há vocabulário controlado de concelhos no
 * backend); esta lista fica aqui, fora de `services/`, para não ser
 * confundida com dado que vem do backend.
 */
export const REGIONS: Region[] = [
  { code: 'PT-LIS', label: 'Lisboa', location: { lat: 38.7223, lon: -9.1393 } },
  { code: 'PT-SNT', label: 'Sintra', location: { lat: 38.8029, lon: -9.3817 } },
  { code: 'PT-CSC', label: 'Cascais', location: { lat: 38.6979, lon: -9.4215 } },
  { code: 'PT-OEI', label: 'Oeiras', location: { lat: 38.687, lon: -9.305 } },
  { code: 'PT-LOU', label: 'Loures', location: { lat: 38.8299, lon: -9.1693 } },
  { code: 'PT-ALM', label: 'Almada', location: { lat: 38.6793, lon: -9.1594 } },
  { code: 'PT-PRT', label: 'Porto', location: { lat: 41.1579, lon: -8.6291 } },
  { code: 'PT-VNG', label: 'Vila Nova de Gaia', location: { lat: 41.1239, lon: -8.6118 } },
  { code: 'PT-MTS', label: 'Matosinhos', location: { lat: 41.1839, lon: -8.6884 } },
  { code: 'PT-BRG', label: 'Braga', location: { lat: 41.5454, lon: -8.4265 } },
  { code: 'PT-CBR', label: 'Coimbra', location: { lat: 40.2033, lon: -8.4103 } },
  { code: 'PT-FAR', label: 'Faro', location: { lat: 37.0194, lon: -7.9304 } },
];

export function regionByCode(code: string): Region {
  return REGIONS.find((region) => region.code === code) ?? REGIONS[0];
}
