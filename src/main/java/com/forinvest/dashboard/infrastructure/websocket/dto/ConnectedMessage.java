package com.forinvest.dashboard.infrastructure.websocket.dto;

/**
 * The greeting a client gets as soon as its connection is open.
 *
 * <p>It carries the subscriber id the server assigned and how often updates will arrive, so a
 * client can tell "the feed is quiet" from "the feed is broken" without being told the interval out
 * of band.
 */
public record ConnectedMessage(String type, String subscriberId, long intervalMillis) {

    public static final String TYPE = "connected";

    public static ConnectedMessage of(String subscriberId, long intervalMillis) {
        return new ConnectedMessage(TYPE, subscriberId, intervalMillis);
    }
}
