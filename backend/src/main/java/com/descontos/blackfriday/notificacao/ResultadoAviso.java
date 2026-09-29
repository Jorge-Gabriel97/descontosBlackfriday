package com.descontos.blackfriday.notificacao;

public enum ResultadoAviso {
    /** E-mail entregue ao servidor SMTP. */
    ENVIADO,
    /** Servidor sem SMTP configurado: o aviso só foi registrado no log. */
    EMAIL_NAO_CONFIGURADO,
    /** O servidor SMTP recusou ou não respondeu. */
    FALHOU
}
