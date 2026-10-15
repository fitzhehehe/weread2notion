package com.aggregator.sdk;

/** 渠道 SDK 返回的账号。发行服务不签发这个号。 */
public record LoginResult(String channelCode, String channelUserId, String channelToken, String nickname) {}
