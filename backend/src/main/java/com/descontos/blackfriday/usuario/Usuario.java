package com.descontos.blackfriday.usuario;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nome;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String senhaHash;

    @Column(nullable = false)
    private Instant criadoEm = Instant.now();

    // O default vale para as contas criadas antes da confirmação por e-mail existir
    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean emailConfirmado;

    protected Usuario() {
    }

    public Usuario(String nome, String email, String senhaHash) {
        this.nome = nome;
        this.email = normalizarEmail(email);
        this.senhaHash = senhaHash;
    }

    public static String normalizarEmail(String email) {
        return email.trim().toLowerCase();
    }

    public Long getId() { return id; }
    public String getNome() { return nome; }
    public String getEmail() { return email; }
    public String getSenhaHash() { return senhaHash; }
    public Instant getCriadoEm() { return criadoEm; }
    public boolean isEmailConfirmado() { return emailConfirmado; }

    public void confirmarEmail() {
        this.emailConfirmado = true;
    }
}
