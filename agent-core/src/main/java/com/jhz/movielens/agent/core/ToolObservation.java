package com.jhz.movielens.agent.core;

import com.jhz.movielens.agent.protocol.ToolResult;

public record ToolObservation(String toolName, ToolResult result) {
}
