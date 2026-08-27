package com.queueflow.ticket;

import com.queueflow.common.ApiException;
import com.queueflow.config.JwtAuthFilter.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService service;

    public record CounterReq(Long counterId) {}

    @GetMapping("/state")
    public Map<String, Object> state(@RequestParam Long counterId) {
        Map<String, Object> result = new HashMap<>();
        result.put("current", service.currentDtoFor(counterId));
        result.put("waiting", service.waitingCount());
        return result;
    }

    @PostMapping("/call-next")
    public TicketDTO callNext(@AuthenticationPrincipal AuthUser user, @RequestBody CounterReq req) {
        return service.callNext(new TicketService.AuthContext(user.id(), req.counterId()));
    }

    @PostMapping("/recall")
    public TicketDTO recall(@AuthenticationPrincipal AuthUser user, @RequestBody CounterReq req) {
        return service.recall(new TicketService.AuthContext(user.id(), req.counterId()));
    }

    @PostMapping("/start")
    public TicketDTO start(@AuthenticationPrincipal AuthUser user, @RequestBody CounterReq req) {
        return service.start(new TicketService.AuthContext(user.id(), req.counterId()));
    }

    @PostMapping("/finish")
    public TicketDTO finish(@AuthenticationPrincipal AuthUser user, @RequestBody CounterReq req) {
        return service.finish(new TicketService.AuthContext(user.id(), req.counterId()));
    }

    @PostMapping("/absent")
    public TicketDTO absent(@AuthenticationPrincipal AuthUser user, @RequestBody CounterReq req) {
        return service.markAbsent(new TicketService.AuthContext(user.id(), req.counterId()));
    }

    @PostMapping("/{id}/cancel")
    public TicketDTO cancel(@PathVariable long id) {
        return service.cancel(id);
    }
}
