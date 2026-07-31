package org.yomirein.sochatserver.netty.codec;


import java.util.List;

import org.yomirein.sochatserver.common.models.MessagePacket;
import static org.yomirein.sochatserver.utils.JsonConfig.MAPPER;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;

// Simple packet decoder that decodes all incoming data
public class PacketDecoder extends MessageToMessageDecoder<Object> {

    // It uses abstract MessageToMessageDecoder with decode() in it

    @Override
    protected void decode(ChannelHandlerContext channelHandlerContext, Object msg, List<Object> out) throws Exception {
        // It's more complex actually
        // For Pings we just retain it
        //
        // But for TextWebSocketFrame we're generating JsonNode from incoming text
        // and then making Type and Payload for MessagePacket, that will be used in WsPacketHandler.java!

        if (msg instanceof WebSocketFrame frame) {
            if (frame instanceof PingWebSocketFrame || frame instanceof PongWebSocketFrame) {
                System.out.println("PONG");
                out.add(frame.retain());
            } else if (frame instanceof TextWebSocketFrame textFrame) {
                String text = textFrame.text();
                JsonNode root = MAPPER.readTree(text);
                String type = root.get("type").asText();
                ObjectNode payload = (ObjectNode) root.get("payload");
                out.add(new MessagePacket(type, payload));
            }
        }
    }
}