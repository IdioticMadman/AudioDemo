package com.robert.audiodemo.network;

public class ConnectorInfo {

    private final String key;
    private final String value;

    /**
     * 根据消息，拆分出来 key 和 value
     */
    public ConnectorInfo(String msg) {
        String[] strings = msg.split(" ");
        key = strings[0];
        if (strings.length == 2) {
            value = strings[1];
        } else {
            value = null;
        }
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }
}
