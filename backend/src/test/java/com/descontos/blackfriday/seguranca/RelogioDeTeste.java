package com.descontos.blackfriday.seguranca;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

class RelogioDeTeste extends Clock {

    private Instant agora = Instant.parse("2026-11-27T10:00:00Z");

    void avancar(Duration tempo) {
        agora = agora.plus(tempo);
    }

    @Override
    public Instant instant() {
        return agora;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }
}
