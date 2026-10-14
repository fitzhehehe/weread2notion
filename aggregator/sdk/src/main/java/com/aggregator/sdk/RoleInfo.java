package com.aggregator.sdk;

public record RoleInfo(
        String type, String roleId, String roleName, int roleLevel, String serverId, String serverName) {}
