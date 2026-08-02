package org.yomirein.sochatserver.common.models;

import org.yomirein.sochatserver.utils.JsonConfig;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.Getter;
import lombok.Setter;

public class MessagePacket {
    @Getter @Setter
    public String type;
    @Getter @Setter
    public ObjectNode payload;

    public MessagePacket(){
        this.payload = JsonConfig.MAPPER.createObjectNode();
    }

    public MessagePacket(String type){
        this.type = type;
        this.payload = JsonConfig.MAPPER.createObjectNode();
    }

    public MessagePacket(String type, ObjectNode payload){
        this.type = type;
        this.payload = payload;
    }

    public static class Builder {

        private String type;
        private final ObjectNode payload = JsonConfig.MAPPER.createObjectNode();

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder put(String field, String value) {
            payload.put(field, value);
            return this;
        }

        public Builder put(String field, int value) {
            payload.put(field, value);
            return this;
        }

        public Builder put(String field, long value) {
            payload.put(field, value);
            return this;
        }

        public Builder put(String field, boolean value) {
            payload.put(field, value);
            return this;
        }

        public Builder putNode(String field, ObjectNode node) {
            payload.set(field, node);
            return this;
        }
        public Builder putNode(String field, ArrayNode node) {
            payload.set(field, node);
            return this;
        }

        public MessagePacket build() {
            return new MessagePacket(type, payload);
        }



    }
}
