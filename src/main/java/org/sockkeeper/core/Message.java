package org.sockkeeper.core;

public record Message(String id, String destUserId, String data, long timestampEpochSecond) {
}
