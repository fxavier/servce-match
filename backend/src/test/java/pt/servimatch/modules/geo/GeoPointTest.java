package pt.servimatch.modules.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class GeoPointTest {

    @Test
    void acceptsValidWgs84Coordinates() {
        // Lisboa
        GeoPoint point = new GeoPoint(38.7223, -9.1393);

        assertThat(point.lat()).isEqualTo(38.7223);
        assertThat(point.lon()).isEqualTo(-9.1393);
    }

    @Test
    void rejectsLatitudeOutOfRange() {
        assertThatThrownBy(() -> new GeoPoint(91, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Latitude");
    }

    @Test
    void rejectsLongitudeOutOfRange() {
        assertThatThrownBy(() -> new GeoPoint(0, 181))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Longitude");
    }
}
