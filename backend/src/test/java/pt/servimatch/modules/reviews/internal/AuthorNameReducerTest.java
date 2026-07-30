package pt.servimatch.modules.reviews.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AuthorNameReducer}: PII reduzida para {@code ReviewWithAuthor.authorName}
 * — "primeiro nome + inicial do apelido", nunca o nome completo.
 */
class AuthorNameReducerTest {

    @ParameterizedTest
    @CsvSource({
            "'Mariana Costa','Mariana C.'",
            "'João Pedro Almeida','João A.'",
            "'  Ana   Correia  ','Ana C.'",
            "'ana correia','ana C.'",
    })
    void reducesToFirstNamePlusLastInitial(String displayName, String expected) {
        assertThat(AuthorNameReducer.reduce(displayName)).isEqualTo(expected);
    }

    @Test
    void singleTokenNameHasNoInitial() {
        assertThat(AuthorNameReducer.reduce("Madonna")).isEqualTo("Madonna");
    }

    @Test
    void neverLeaksTheFullLastName() {
        String reduced = AuthorNameReducer.reduce("Mariana Costa");
        assertThat(reduced).doesNotContain("Costa");
    }

    @Test
    void nullOrBlankReturnsEmptyStringInsteadOfThrowing() {
        assertThat(AuthorNameReducer.reduce(null)).isEmpty();
        assertThat(AuthorNameReducer.reduce("   ")).isEmpty();
        assertThat(AuthorNameReducer.reduce("")).isEmpty();
    }
}
