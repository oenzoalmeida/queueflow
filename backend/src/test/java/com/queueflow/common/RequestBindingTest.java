package com.queueflow.common;

import com.queueflow.counter.CounterController.CounterReq;
import com.queueflow.queue.QueueController.QueueReq;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Garante o comportamento do Jackson 2 para "active" ausente/null (false) e explícito. */
class RequestBindingTest {
    private final JsonMapper mapper = JsonMapper.builder().build();

    private boolean queueActive(String json) {
        return Boolean.TRUE.equals(mapper.readValue(json, QueueReq.class).active());
    }

    private boolean counterActive(String json) {
        return Boolean.TRUE.equals(mapper.readValue(json, CounterReq.class).active());
    }

    @Test
    void queueActiveAbsentNullTrueFalse() {
        assertEquals(false, queueActive("{\"name\":\"A\",\"prefix\":\"A\"}"));
        assertEquals(false, queueActive("{\"name\":\"A\",\"prefix\":\"A\",\"active\":null}"));
        assertEquals(true, queueActive("{\"name\":\"A\",\"prefix\":\"A\",\"active\":true}"));
        assertEquals(false, queueActive("{\"name\":\"A\",\"prefix\":\"A\",\"active\":false}"));
    }

    @Test
    void counterActiveAbsentNullTrueFalse() {
        assertEquals(false, counterActive("{\"name\":\"G\"}"));
        assertEquals(false, counterActive("{\"name\":\"G\",\"active\":null}"));
        assertEquals(true, counterActive("{\"name\":\"G\",\"active\":true}"));
        assertEquals(false, counterActive("{\"name\":\"G\",\"active\":false}"));
    }
}
