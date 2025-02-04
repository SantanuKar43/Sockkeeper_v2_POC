package org.sockkeeper.resources.v3;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Message {
    private String receiver;
    private String value;

    public Message(String receiver, String value) {
        this.receiver = receiver;
        this.value = value;
    }
}
