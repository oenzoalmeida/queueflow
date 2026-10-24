package com.queueflow.dashboard;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposição HTTP do painel. A apuração dos números vive em DashboardService.
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboard;

    @GetMapping("/today")
    public DashboardService.DashboardDTO today() {
        return dashboard.resumoDeHoje();
    }
}
