package com.descontos.blackfriday.seguranca;

import com.descontos.blackfriday.monitoramento.MonitoramentoService;
import com.descontos.blackfriday.notificacao.ResultadoAviso;
import com.descontos.blackfriday.usuario.Usuario;
import com.descontos.blackfriday.usuario.UsuarioRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

import java.net.URI;
import java.util.Optional;

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

    public record UsuarioResponse(Long id, String nome, String email, boolean emailConfirmado) {
        static UsuarioResponse de(Usuario u) {
            return new UsuarioResponse(u.getId(), u.getNome(), u.getEmail(), u.isEmailConfirmado());
        }
    }

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository contextos;
    private final CsrfAuthenticationStrategy csrfStrategy;
    private final LimiteDeTentativas limite;
    private final ConfirmacaoEmail confirmacao;
    private final MonitoramentoService monitoramentos;

    public AuthController(UsuarioRepository usuarios, PasswordEncoder encoder,
                          AuthenticationManager authenticationManager, SecurityContextRepository contextos,
                          CsrfTokenRepository csrfTokens, LimiteDeTentativas limite, ConfirmacaoEmail confirmacao,
                          MonitoramentoService monitoramentos) {
        this.monitoramentos = monitoramentos;
        this.limite = limite;
        this.confirmacao = confirmacao;
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
        Usuario novo = usuarios.save(new Usuario(req.nome().trim(), email, encoder.encode(req.senha())));
        confirmacao.enviar(novo);
        return entrar(email, req.senha(), request, response);
    }

    @PostMapping("/login")
    public UsuarioResponse login(@Valid @RequestBody LoginRequest req,
                                 HttpServletRequest request, HttpServletResponse response) {
        String email = Usuario.normalizarEmail(req.email());
        String ip = request.getRemoteAddr();
        if (limite.bloqueado(email, ip)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Muitas tentativas. Tente de novo em " + limite.bloqueio().toMinutes() + " minutos.");
        }
        try {
            UsuarioResponse usuario = entrar(email, req.senha(), request, response);
            limite.registrarSucesso(email);
            return usuario;
        } catch (ResponseStatusException e) {
            limite.registrarFalha(email, ip);
            throw e;
        }
    }

    @GetMapping("/eu")
    public UsuarioResponse eu(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return carregar(usuario.id());
    }

    @GetMapping("/confirmar-email")
    public ResponseEntity<Void> confirmarEmail(@RequestParam(required = false) String token) {
        Optional<Long> confirmado = confirmacao.confirmar(token);
        // Depois do commit da confirmação, para o SMTP não segurar a transação
        confirmado.ifPresent(monitoramentos::enviarAvisosRetidos);
        String resultado = confirmado.isPresent() ? "confirmado" : "link-invalido";
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(confirmacao.urlPublica() + "/?email=" + resultado))
                .build();
    }

    @PostMapping("/reenviar-confirmacao")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reenviarConfirmacao(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        if (confirmacao.reenviar(usuario.id()) == ResultadoAviso.FALHOU) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Não foi possível enviar o e-mail agora. Tente de novo mais tarde.");
        }
    }

    // A sessão guarda uma cópia do usuário; a confirmação do e-mail só aparece lendo do banco
    private UsuarioResponse carregar(Long id) {
        return usuarios.findById(id).map(UsuarioResponse::de)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
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

        return carregar(((UsuarioAutenticado) auth.getPrincipal()).id());
    }
}
