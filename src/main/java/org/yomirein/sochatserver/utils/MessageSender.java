package org.yomirein.sochatserver.utils;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yomirein.sochatserver.common.models.MessagePacket;
import org.yomirein.sochatserver.sessions.Session;
import org.yomirein.sochatserver.sessions.SessionManager;
import org.yomirein.sochatserver.users.User;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;

// A little class for handlers where it generates easy messages to send users
public class MessageSender {

    // Send json, I also used the same method in https://github.com/yomirein/AuthenticationServer :D
    public static void sendHttp(ChannelHandlerContext ctx, HttpResponseStatus httpResponseStatus, MessagePacket messagePacket) throws JsonProcessingException {
        String json = JsonConfig.MAPPER.writeValueAsString(messagePacket);
        sendHttpJson(ctx, httpResponseStatus, json);
    }
    // A little bit modified sendHttpJson cuz i don't want use Jackson too much
    public static void sendHttp(ChannelHandlerContext ctx, HttpResponseStatus httpResponseStatus, String message) {
        sendHttpJson(ctx, httpResponseStatus, message);
    }


    public static void sendHttpJson(ChannelHandlerContext ctx, HttpResponseStatus httpResponseStatus, String message) {
        ByteBuf content = Unpooled.copiedBuffer(
                message, CharsetUtil.UTF_8
        );

        //Then we make a response that we can send, making response status and setting our content for response
        FullHttpResponse response =
                new DefaultFullHttpResponse(
                        HttpVersion.HTTP_1_1,
                        httpResponseStatus,
                        content
                );


        // Setting basic headers
        response.headers().set(
                HttpHeaderNames.CONTENT_TYPE,
                "application/json; charset=UTF-8"
        );
        response.headers().setInt(
                HttpHeaderNames.CONTENT_LENGTH,
                content.readableBytes()
        );

        // Sending to user!!
        ctx.writeAndFlush(response);
    }

    // Send error
    public static void sendError(ChannelHandlerContext ctx,
                                 MessagePacket request,
                                 String errorMessage) {
        MessagePacket packet;
        if (request.getPayload().get("requestId") != null) {
            packet = new MessagePacket.Builder()
                    .type(request.getType())
                    .put("success", false)
                    .put("requestId", request.getPayload().get("requestId").asText())
                    .put("server_message", errorMessage)
                    .build();
        }
        else {
             packet = new MessagePacket.Builder()
                    .type(request.getType())
                    .put("success", false)
                    .put("server_message", errorMessage)
                    .build();
        }

        ctx.channel().writeAndFlush(packet);
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(MessageSender.class);
    
    public static void handleError(ChannelHandlerContext ctx, MessagePacket packet, Exception e) {
        LOGGER.error("Caught an exception", e);
        sendError(ctx, packet, e.getMessage());
    }

    // Generate simple response
    public static MessagePacket.Builder buildBaseResponse(MessagePacket request, String message) {
        if (request.getPayload().get("requestId") != null) {
            return new MessagePacket.Builder()
                    .type(request.getType())
                    .put("success", true)
                    .put("requestId", request.getPayload().get("requestId").asText())
                    .put("server_message", message);
        }
        return new MessagePacket.Builder()
                .type(request.getType())
                .put("server_message", message);
    }

    // Notify every connected user on specific account
    public static void notifyUser(User user, MessagePacket packet, SessionManager sessionManager) {
        Set<Session> sessions = sessionManager.getUserSessions(user);
        notifyUser(sessions, packet);
    }
    public static void notifyUser(Set<Session> sessions, MessagePacket packet) {
        for (Session session : sessions) {
            session.getChannel().writeAndFlush(packet);
        }
    }
}
