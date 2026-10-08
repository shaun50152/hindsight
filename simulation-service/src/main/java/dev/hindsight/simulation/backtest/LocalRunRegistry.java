package dev.hindsight.simulation.backtest;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class LocalRunRegistry {

    private final Map<UUID, LocalBacktestRunner.LocalRunHandle> handles = new ConcurrentHashMap<>();

    public void register(LocalBacktestRunner.LocalRunHandle handle) {
        handles.put(handle.backtestId(), handle);
    }

    public LocalBacktestRunner.LocalRunHandle get(UUID backtestId) {
        return handles.get(backtestId);
    }

    public void remove(UUID backtestId) {
        handles.remove(backtestId);
    }
}
