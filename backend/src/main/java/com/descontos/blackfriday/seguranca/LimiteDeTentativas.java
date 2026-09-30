package com.descontos.blackfriday.seguranca;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LimiteDeTentativas {

    private record Falhas(int quantidade, Instant expiraEm) {
    }

    private static final int LIMPAR_ACIMA_DE = 10_000;

    private final Map<String, Falhas> falhas = new ConcurrentHashMap<>();
    private final int maxPorEmail;
    private final int maxPorIp;
    private final Duration bloqueio;
    private final Clock relogio;

    @Autowired
    public LimiteDeTentativas(@Value("${app.login.max-falhas-por-email}") int maxPorEmail,
                              @Value("${app.login.max-falhas-por-ip}") int maxPorIp,
                              @Value("${app.login.bloqueio}") Duration bloqueio) {
        this(maxPorEmail, maxPorIp, bloqueio, Clock.systemUTC());
    }

    LimiteDeTentativas(int maxPorEmail, int maxPorIp, Duration bloqueio, Clock relogio) {
        this.maxPorEmail = maxPorEmail;
        this.maxPorIp = maxPorIp;
        this.bloqueio = bloqueio;
        this.relogio = relogio;
    }

    public Duration bloqueio() {
        return bloqueio;
    }

    public boolean bloqueado(String email, String ip) {
        return atingiu("email:" + email, maxPorEmail) || atingiu("ip:" + ip, maxPorIp);
    }

    public void registrarFalha(String email, String ip) {
        Instant agora = relogio.instant();
        if (falhas.size() > LIMPAR_ACIMA_DE) {
            falhas.values().removeIf(f -> !agora.isBefore(f.expiraEm()));
        }
        somar("email:" + email, agora);
        somar("ip:" + ip, agora);
    }

    // Só o e-mail: zerar o IP deixaria um atacante "limpar" o contador entrando na própria conta
    public void registrarSucesso(String email) {
        falhas.remove("email:" + email);
    }

    private boolean atingiu(String chave, int maximo) {
        Falhas f = falhas.get(chave);
        return f != null && f.quantidade() >= maximo && relogio.instant().isBefore(f.expiraEm());
    }

    private void somar(String chave, Instant agora) {
        falhas.compute(chave, (k, f) -> {
            int anteriores = f == null || !agora.isBefore(f.expiraEm()) ? 0 : f.quantidade();
            return new Falhas(anteriores + 1, agora.plus(bloqueio));
        });
    }
}
