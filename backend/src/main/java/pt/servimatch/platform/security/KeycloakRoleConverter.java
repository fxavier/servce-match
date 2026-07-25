package pt.servimatch.platform.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Extrai as roles do realm Keycloak (por omissão, a claim aninhada
 * {@code realm_access.roles}) e converte-as em {@link GrantedAuthority} no
 * formato {@code ROLE_<ROLE>} (convenção do Spring Security para
 * {@code hasRole(...)}), maiúsculas.
 *
 * <p>Roles de domínio esperadas: {@code CUSTOMER}, {@code PROVIDER},
 * {@code ADMIN} (ADR-0002, ARQUITETURA §8.4). O caminho da claim é
 * configurável ({@code servimatch.security.jwt.roles-claim}) para acomodar
 * roles de client (Keycloak) em vez de realm, sem alterar código.
 */
public class KeycloakRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private final List<String> claimPath;

    public KeycloakRoleConverter(String dottedClaimPath) {
        this.claimPath = List.of(dottedClaimPath.split("\\."));
    }

    @Override
    @SuppressWarnings("unchecked")
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Object current = jwt.getClaims();
        for (String segment : claimPath) {
            if (!(current instanceof Map<?, ?> map)) {
                return Collections.emptyList();
            }
            current = map.get(segment);
            if (current == null) {
                return Collections.emptyList();
            }
        }
        if (!(current instanceof List<?> roles)) {
            return Collections.emptyList();
        }
        return roles.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(role -> "ROLE_" + role.toUpperCase(Locale.ROOT))
                .<GrantedAuthority>map(SimpleGrantedAuthority::new)
                .collect(Collectors.toUnmodifiableList());
    }
}
