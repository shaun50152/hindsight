package dev.hindsight.policy.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

public class JwtRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        Object rolesClaim = jwt.getClaim("roles");
        if (rolesClaim instanceof Collection<?> roles) {
            for (Object role : roles) {
                if (role != null) {
                    String name = role.toString();
                    if (!name.startsWith("ROLE_")) {
                        name = "ROLE_" + name;
                    }
                    authorities.add(new SimpleGrantedAuthority(name));
                }
            }
        }
        Object authoritiesClaim = jwt.getClaim("authorities");
        if (authoritiesClaim instanceof Collection<?> authList) {
            for (Object auth : authList) {
                if (auth != null) {
                    authorities.add(new SimpleGrantedAuthority(auth.toString()));
                }
            }
        }
        return authorities;
    }
}
