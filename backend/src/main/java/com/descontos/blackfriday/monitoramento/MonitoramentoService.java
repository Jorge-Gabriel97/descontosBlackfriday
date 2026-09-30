package com.descontos.blackfriday.monitoramento;

import com.descontos.blackfriday.loja.Loja;
import com.descontos.blackfriday.loja.Lojas;
import com.descontos.blackfriday.loja.ProdutoLoja;
import com.descontos.blackfriday.notificacao.NotificadorEmail;
import com.descontos.blackfriday.notificacao.ResultadoAviso;
import com.descontos.blackfriday.usuario.Usuario;
import com.descontos.blackfriday.usuario.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class MonitoramentoService {

    private static final Logger log = LoggerFactory.getLogger(MonitoramentoService.class);

    private record Chave(Loja loja, String codigo) {
    }

    private final ProdutoMonitoradoRepository repository;
    private final UsuarioRepository usuarios;
    private final Lojas lojas;
    private final NotificadorEmail notificador;

    public MonitoramentoService(ProdutoMonitoradoRepository repository, UsuarioRepository usuarios,
                                Lojas lojas, NotificadorEmail notificador) {
        this.repository = repository;
        this.usuarios = usuarios;
        this.lojas = lojas;
        this.notificador = notificador;
    }

    public List<ProdutoMonitorado> listar(Long usuarioId) {
        return repository.findAllByUsuarioIdOrderByCriadoEmDesc(usuarioId);
    }

    public ProdutoMonitorado cadastrar(Long usuarioId, Loja loja, String codigo, BigDecimal precoMaximo) {
        if (repository.existsByUsuarioIdAndLojaAndCodigoProduto(usuarioId, loja, codigo)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Você já monitora este produto");
        }
        Usuario usuario = usuarios.findById(usuarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        ProdutoLoja produto = consultarOuFalhar(loja, codigo);
        return aplicarPreco(new ProdutoMonitorado(usuario, produto, precoMaximo), produto);
    }

    public ProdutoMonitorado verificar(Long usuarioId, Long id) {
        ProdutoMonitorado monitorado = buscarDoUsuario(usuarioId, id);
        return aplicarPreco(monitorado, consultarOuFalhar(monitorado.getLoja(), monitorado.getCodigoProduto()));
    }

    public void enviarAvisosRetidos(Long usuarioId) {
        repository.findAllByUsuarioIdOrderByCriadoEmDesc(usuarioId).stream()
                .filter(m -> m.getUltimoAvisoResultado() == ResultadoAviso.EMAIL_NAO_CONFIRMADO)
                .forEach(m -> {
                    m.registrarAviso(notificador.notificar(m));
                    repository.save(m);
                });
    }

    public void remover(Long usuarioId, Long id) {
        repository.delete(buscarDoUsuario(usuarioId, id));
    }

    @Scheduled(fixedDelayString = "${app.monitor.intervalo}", initialDelayString = "${app.monitor.atraso-inicial}")
    public void verificarTodos() {
        Map<Chave, List<ProdutoMonitorado>> porProduto = repository.findAll().stream()
                .filter(m -> lojas.ativa(m.getLoja()))
                .collect(Collectors.groupingBy(m -> new Chave(m.getLoja(), m.getCodigoProduto())));
        log.info("Verificando {} produto(s)", porProduto.size());

        porProduto.forEach((chave, monitorados) -> {
            Optional<ProdutoLoja> produto = lojas.cliente(chave.loja()).consultar(chave.codigo());
            if (produto.isEmpty()) {
                log.warn("Produto {} de {} não pôde ser consultado; tentando no próximo ciclo",
                        chave.codigo(), chave.loja().getNome());
                return;
            }
            monitorados.forEach(m -> aplicarPreco(m, produto.get()));
        });
    }

    private ProdutoMonitorado aplicarPreco(ProdutoMonitorado monitorado, ProdutoLoja produto) {
        boolean avisar = monitorado.registrarPreco(produto);
        boolean confirmado = monitorado.getUsuario().isEmailConfirmado();
        log.info("Monitoramento {} ({} {}): à vista {}, máximo {}, disponível {} -> {}",
                monitorado.getId(), monitorado.getLoja().getNome(), monitorado.getCodigoProduto(),
                produto.precoPix(), monitorado.getPrecoMaximo(), produto.disponivel(),
                !avisar ? "sem aviso" : confirmado ? "enviando aviso" : "aviso retido (e-mail não confirmado)");
        if (avisar) {
            monitorado.registrarAviso(confirmado ? notificador.notificar(monitorado) : ResultadoAviso.EMAIL_NAO_CONFIRMADO);
        }
        return repository.save(monitorado);
    }

    private ProdutoMonitorado buscarDoUsuario(Long usuarioId, Long id) {
        return repository.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Monitoramento não encontrado"));
    }

    private ProdutoLoja consultarOuFalhar(Loja loja, String codigo) {
        return lojas.cliente(loja).consultar(codigo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Não foi possível consultar o produto no " + loja.getNome()));
    }
}
