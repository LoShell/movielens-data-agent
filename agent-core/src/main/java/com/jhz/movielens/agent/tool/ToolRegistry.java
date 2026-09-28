package com.jhz.movielens.agent.tool;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ToolRegistry {
    private final Map<String, Tool> tools = new LinkedHashMap<>();

    public void register(Tool tool) {
        if (tool == null || tool.name() == null || tool.name().isBlank()) {
            throw new IllegalArgumentException("tool and tool name must not be blank");
        }
        if (tools.putIfAbsent(tool.name(), tool) != null) {
            throw new IllegalArgumentException("duplicate tool name: " + tool.name());
        }
    }

    public Optional<Tool> find(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    public Collection<Tool> all() {
        return List.copyOf(tools.values());
    }
}
