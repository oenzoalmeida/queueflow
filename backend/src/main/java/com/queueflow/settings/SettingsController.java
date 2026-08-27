package com.queueflow.settings;

import com.queueflow.common.ApiException;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final PrioritySettingsRepository repo;

    public record Req(@Min(0) @Max(99) int normalsBeforePriority) {}

    @GetMapping("/priority")
    public Map<String, Object> get() {
        return Map.of("normalsBeforePriority", repo.get().getNormalsBeforePriority());
    }

    @PutMapping("/priority")
    public Map<String, Object> update(@RequestBody @jakarta.validation.Valid Req req) {
        PrioritySettings s = repo.get();
        s.setNormalsBeforePriority(req.normalsBeforePriority());
        return Map.of("normalsBeforePriority", repo.save(s).getNormalsBeforePriority());
    }
}
