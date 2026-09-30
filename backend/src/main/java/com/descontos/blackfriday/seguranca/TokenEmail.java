package com.descontos.blackfriday.seguranca;

import com.descontos.blackfriday.config.EnumComoTexto;
import com.descontos.blackfriday.usuario.Usuario;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
public class TokenEmail {

    public enum Finalidade { CONFIRMAR_EMAIL, TROCAR_SENHA }

    public static class FinalidadeComoTexto extends EnumComoTexto<Finalidade> {
        public FinalidadeComoTexto() {
            super(Finalidade.class);
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Usuario usuario;

    @Convert(converter = FinalidadeComoTexto.class)
    @Column(nullable = false, length = 20)
    private Finalidade finalidade;

    // SHA-256 do token; o token em si só existe no link enviado por e-mail
    @Column(nullable = false, unique = true, length = 64)
    private String hash;

    @Column(nullable = false)
    private Instant criadoEm;

    @Column(nullable = false)
    private Instant expiraEm;

    protected TokenEmail() {
    }

    TokenEmail(Usuario usuario, Finalidade finalidade, String hash, Instant criadoEm, Instant expiraEm) {
        this.usuario = usuario;
        this.finalidade = finalidade;
        this.hash = hash;
        this.criadoEm = criadoEm;
        this.expiraEm = expiraEm;
    }

    public Usuario getUsuario() { return usuario; }
    public String getHash() { return hash; }
    public Instant getCriadoEm() { return criadoEm; }
    public Instant getExpiraEm() { return expiraEm; }
}
