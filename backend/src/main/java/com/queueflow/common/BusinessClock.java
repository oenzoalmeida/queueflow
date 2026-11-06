package com.queueflow.common;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * O "dia" do negócio: virada da numeração diária das senhas, apuração do
 * dashboard e filtro padrão do histórico.
 *
 * Antes cada ponto usava ZoneId.systemDefault(): no container de produção o
 * fuso é UTC, então a numeração e os indicadores viravam o dia às 21h no
 * horário de Brasília. Fica fixo em America/Sao_Paulo.
 *
 * ATENÇÃO: o índice único uq_daily_seq_numbers usa a função SQL
 * ticket_issue_day(), que precisa estar no MESMO fuso (ver db/migration/V4).
 * Se o fuso do negócio mudar aqui, a função precisa mudar junto em uma nova
 * migração; BusinessDayParityIT falha se os dois divergirem.
 */
@Component
public class BusinessClock {

    public static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Sao_Paulo");

    private final ZoneId zone;

    public BusinessClock() {
        this(BUSINESS_ZONE);
    }

    /** Testes podem fixar outro fuso para exercitar a virada de dia. */
    public BusinessClock(ZoneId zone) {
        this.zone = zone;
    }

    public ZoneId zone() {
        return zone;
    }

    public LocalDate today() {
        return LocalDate.now(zone);
    }
}
