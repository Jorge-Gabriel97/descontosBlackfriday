package com.descontos.blackfriday.seguranca;

import com.descontos.blackfriday.usuario.Usuario;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public record UsuarioAutenticado(Long id, String nome, String email, String senhaHash) implements UserDetails {

    static UsuarioAutenticado de(Usuario u) {
        return new UsuarioAutenticado(u.getId(), u.getNome(), u.getEmail(), u.getSenhaHash());
    }

    // A sessão é gravada no banco; o hash da senha só serve para o login e não precisa ir junto
    UsuarioAutenticado semSenha() {
        return new UsuarioAutenticado(id, nome, email, null);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USUARIO"));
    }

    @Override
    public String getPassword() {
        return senhaHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String toString() {
        return "UsuarioAutenticado[id=" + id + ", email=" + email + "]";
    }
}
