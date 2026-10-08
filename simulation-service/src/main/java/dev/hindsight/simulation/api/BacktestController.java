package dev.hindsight.simulation.api;

import dev.hindsight.simulation.api.dto.CreateBacktestRequest;
import dev.hindsight.simulation.security.SimulationRoles;
import dev.hindsight.simulation.service.BacktestApplicationService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/backtests")
public class BacktestController {

    private final BacktestApplicationService backtestApplicationService;

    public BacktestController(BacktestApplicationService backtestApplicationService) {
        this.backtestApplicationService = backtestApplicationService;
    }

    @PostMapping
    @PreAuthorize("hasRole('" + SimulationRoles.OPS + "')")
    BacktestApplicationService.BacktestView start(@Valid @RequestBody CreateBacktestRequest request) {
        return backtestApplicationService.start(request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('" + SimulationRoles.OPS + "','" + SimulationRoles.AUDITOR + "')")
    BacktestApplicationService.BacktestView get(@PathVariable UUID id) {
        return backtestApplicationService.get(id);
    }
}
