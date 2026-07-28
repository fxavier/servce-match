import { useMemo } from 'react';
import { Marker, MapContainer, TileLayer } from 'react-leaflet';
import type { LeafletEventHandlerFnMap, Marker as LeafletMarker } from 'leaflet';
import { Field } from '../../../components/ui/Field';
import { Input } from '../../../components/ui/Input';
import { Select } from '../../../components/ui/Select';
import { REGIONS } from '../../../constants/regions';
import 'leaflet/dist/leaflet.css';

export interface StepAddressValue {
  line1: string;
  postalCode: string;
  city: string;
  regionCode: string;
  lat: number;
  lon: number;
}

export interface StepAddressProps {
  value: StepAddressValue;
  onChange: (patch: Partial<StepAddressValue>) => void;
  errors: Partial<Record<keyof StepAddressValue, string>>;
}

function DraggableMarker({ lat, lon, onMove }: { lat: number; lon: number; onMove: (lat: number, lon: number) => void }) {
  const eventHandlers = useMemo<LeafletEventHandlerFnMap>(
    () => ({
      dragend(event) {
        const marker = event.target as LeafletMarker;
        const position = marker.getLatLng();
        onMove(position.lat, position.lng);
      },
    }),
    [onMove],
  );

  return <Marker draggable position={[lat, lon]} eventHandlers={eventHandlers} />;
}

export function StepAddress({ value, onChange, errors }: StepAddressProps) {
  function handleRegionChange(regionCode: string) {
    const region = REGIONS.find((candidate) => candidate.code === regionCode);
    onChange({
      regionCode,
      city: region?.label ?? value.city,
      lat: region?.location.lat ?? value.lat,
      lon: region?.location.lon ?? value.lon,
    });
  }

  return (
    <div className="flex flex-col gap-5">
      <div>
        <h2 className="text-h2 font-display font-bold text-foreground">Onde é o serviço?</h2>
        <p className="mt-2 text-body text-muted">A morada exata só é partilhada com o prestador depois de aceitar uma proposta.</p>
      </div>

      <Field id="wizard-line1" label="Morada" required error={errors.line1}>
        <Input
          id="wizard-line1"
          value={value.line1}
          onChange={(event) => onChange({ line1: event.target.value })}
          invalid={Boolean(errors.line1)}
          placeholder="Rua, número, andar"
        />
      </Field>

      <div className="grid grid-cols-2 gap-4">
        <Field id="wizard-postal" label="Código postal" required error={errors.postalCode}>
          <Input
            id="wizard-postal"
            value={value.postalCode}
            onChange={(event) => onChange({ postalCode: event.target.value })}
            invalid={Boolean(errors.postalCode)}
            placeholder="1000-001"
          />
        </Field>
        <Field id="wizard-region" label="Concelho" required error={errors.regionCode}>
          <Select
            id="wizard-region"
            value={value.regionCode}
            onChange={(event) => handleRegionChange(event.target.value)}
            invalid={Boolean(errors.regionCode)}
          >
            <option value="">Escolha o concelho</option>
            {REGIONS.map((region) => (
              <option key={region.code} value={region.code}>
                {region.label}
              </option>
            ))}
          </Select>
        </Field>
      </div>

      <Field id="wizard-city" label="Localidade" required error={errors.city}>
        <Input id="wizard-city" value={value.city} onChange={(event) => onChange({ city: event.target.value })} invalid={Boolean(errors.city)} />
      </Field>

      <div>
        <p className="text-sm font-medium text-foreground">Ajuste o marcador no mapa se precisar</p>
        <div className="mt-2 h-56 overflow-hidden rounded-lg border border-line">
          <MapContainer center={[value.lat, value.lon]} zoom={12} className="h-full w-full" scrollWheelZoom={false}>
            <TileLayer
              attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
              url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
            />
            <DraggableMarker lat={value.lat} lon={value.lon} onMove={(lat, lon) => onChange({ lat, lon })} />
          </MapContainer>
        </div>
      </div>
    </div>
  );
}
