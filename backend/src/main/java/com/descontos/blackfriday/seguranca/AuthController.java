package com.descontos.blackfriday.seguranca;

import com.descontos.blackfriday.usuario.Usuario;
import com.descontos.blackfriday.usuario.UsuarioRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record CadastroRequest(
            @NotBlank @Size(max = 100) String nome,
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 8, max = 72) String senha) {
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String senha) {
    }

    public record UsuarioResponse(Long id, String nome, String email) {
        static UsuarioResponse de(UsuarioAutenticado u) {
            return new UsuarioResponse(u.id(), u.nome(), u.email());
        }
    }

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository contextos;
    private final CsrfAuthenticationStrategy csrfStrategy;

    public AuthController(UsuarioRepository usuarios, PasswordEncoder encoder,
                          AuthenticationManager authenticationManager, SecurityContextRepository contextos,
                          CsrfTokenRepository csrfTokens) {
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.authenticationManager = authenticationManager;
        this.contextos = contextos;
        this.csrfStrategy = new CsrfAuthenticationStrategy(csrfTokens);
    }

    @PostMapping("/cadastro")
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponse cadastrar(@Valid @RequestBody CadastroRequest req,
                                     HttpServletRequest request, HttpServletResponse response) {
        String email = Usuario.normalizarEmail(req.email());
        if (usuarios.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe uma conta com este e-mail");
        }
        usuarios.save(new Usuario(req.nome().trim(), email, encoder.encode(req.senha())));
        return entrar(email, req.senha(), request, response);
    }

    @PostMapping("/login")
    public UsuarioResponse login(@Valid @RequestBody LoginRequest req,
                                 HttpServletRequest request, HttpServletResponse response) {
        return entrar(req.email(), req.senha(), request, response);
    }

    @GetMapping("/eu")
    public UsuarioResponse eu(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return UsuarioResponse.de(usuario);
    }

    private UsuarioResponse entrar(String email, String senha, HttpServletRequest request, HttpServletResponse response) {
        Authentication auth;
        try {
            auth = authenticationManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(email, senha));
        } catch (AuthenticationException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "E-mail ou senha inválidos");
        }

        // Troca o id da sessão e o token CSRF no login (evita fixação de sessão)
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        csrfStrategy.onAuthentication(auth, request, response);

        SecurityContext contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(auth);
        SecurityContextHolder.setContext(contexto);
        contextos.saveContext(contexto, request, response);

        return UsuarioResponse.de((UsuarioAutenticado) auth.getPrincipal());
    }
}
