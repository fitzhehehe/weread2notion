package com.nearme.game.sdk.common.model.biz;

import java.util.TreeMap;

public class ReportUserGameInfoParam {
    public ReportUserGameInfoParam(
            String roleId,
            String roleName,
            int roleLevel,
            String serverId,
            String serverName,
            String chapter,
            TreeMap<String, Number> ext) {}
}
