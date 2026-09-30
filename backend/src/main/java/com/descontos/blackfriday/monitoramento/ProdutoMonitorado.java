package com.descontos.blackfriday.monitoramento;

import com.descontos.blackfriday.config.EnumComoTexto;
import com.descontos.blackfriday.loja.Loja;
import com.descontos.blackfriday.loja.ProdutoLoja;
import com.descontos.blackfriday.notificacao.ResultadoAviso;
import com.descontos.blackfriday.usuario.Usuario;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"usuario_id", "loja", "codigoProduto"}))
public class ProdutoMonitorado {

    public static class LojaComoTexto extends EnumComoTexto<Loja> {
        public LojaComoTexto() {
            super(Loja.class);
        }
    }

    public static class ResultadoComoTexto extends EnumComoTexto<ResultadoAviso> {
        public ResultadoComoTexto() {
            super(ResultadoAviso.class);
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    private Usuario usuario;

    @Convert(converter = LojaComoTexto.class)
    @Column(nullable = false, length = 30)
    private Loja loja;

    @Column(nullable = false, length = 60)
    private String codigoProduto;

    @Column(nullable = false, length = 500)
    private String nome;

    @Column(nullable = false, length = 1000)
    private String link;

    @Column(length = 1000)
    private String imagem;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal precoMaximo;

    @Column(precision = 12, scale = 2)
    private BigDecimal precoAtual;

    @Column(precision = 12, scale = 2)
    private BigDecimal precoPixAtual;

    private boolean disponivel;

    @Column(precision = 12, scale = 2)
    private BigDecimal precoNotificado;

    @Convert(converter = ResultadoComoTexto.class)
    @Column(length = 30)
    private ResultadoAviso ultimoAvisoResultado;

    private Instant ultimoAvisoEm;

    @Column(nullable = false)
    private Instant criadoEm = Instant.now();

    private Instant ultimaVerificacao;

    protected ProdutoMonitorado() {
    }

    public ProdutoMonitorado(Usuario usuario, ProdutoLoja produto, BigDecimal precoMaximo) {
        this.usuario = usuario;
        this.loja = produto.loja();
        this.codigoProduto = produto.codigo();
        this.precoMaximo = precoMaximo;
        this.nome = produto.nome();
        this.link = produto.link();
        this.imagem = produto.imagem();
    }

    /** Depois de um aviso entregue, só avisa de novo se o preço cair mais ou se subir e voltar. */
    public boolean registrarPreco(ProdutoLoja produto) {
        this.nome = produto.nome();
        this.link = produto.link();
        this.imagem = produto.imagem();
        this.precoAtual = produto.preco();
        this.precoPixAtual = produto.precoPix();
        this.disponivel = produto.disponivel();
        this.ultimaVerificacao = Instant.now();

        BigDecimal melhorPreco = produto.precoPix();
        if (!produto.disponivel() || melhorPreco.compareTo(precoMaximo) > 0) {
            this.precoNotificado = null;
            return false;
        }
        return precoNotificado == null || melhorPreco.compareTo(precoNotificado) < 0;
    }

    // Só um aviso entregue conta; se o e-mail falhou, a próxima verificação tenta de novo
    public void registrarAviso(ResultadoAviso resultado) {
        this.ultimoAvisoResultado = resultado;
        this.ultimoAvisoEm = Instant.now();
        if (resultado == ResultadoAviso.ENVIADO) {
            this.precoNotificado = precoPixAtual;
        }
    }

    public Long getId() { return id; }
    public Usuario getUsuario() { return usuario; }
    public Loja getLoja() { return loja; }
    public String getCodigoProduto() { return codigoProduto; }
    public String getNome() { return nome; }
    public String getLink() { return link; }
    public String getImagem() { return imagem; }
    public BigDecimal getPrecoMaximo() { return precoMaximo; }
    public BigDecimal getPrecoAtual() { return precoAtual; }
    public BigDecimal getPrecoPixAtual() { return precoPixAtual; }
    public boolean isDisponivel() { return disponivel; }
    public BigDecimal getPrecoNotificado() { return precoNotificado; }
    public ResultadoAviso getUltimoAvisoResultado() { return ultimoAvisoResultado; }
    public Instant getUltimoAvisoEm() { return ultimoAvisoEm; }
    public Instant getCriadoEm() { return criadoEm; }
    public Instant getUltimaVerificacao() { return ultimaVerificacao; }
}
