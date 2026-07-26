package pt.servimatch.modules.search.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SearchCursorTest {

    @Test
    void roundTripsAnOffset() {
        String cursor = SearchCursor.encode(40);

        assertThat(SearchCursor.decode(cursor)).isEqualTo(40);
    }

    @Test
    void rejectsGarbageCursor() {
        assertThatThrownBy(() -> SearchCursor.decode("not-a-valid-cursor!!"))
                .isInstanceOf(InvalidSearchParametersException.class);
    }

    @Test
    void rejectsCursorEncodingANegativeOffset() {
        String tampered = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("off:-5".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThatThrownBy(() -> SearchCursor.decode(tampered))
                .isInstanceOf(InvalidSearchParametersException.class);
    }
}
