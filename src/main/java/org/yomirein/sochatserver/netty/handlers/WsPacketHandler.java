package org.yomirein.sochatserver.netty.handlers;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yomirein.sochatserver.auth.AuthHandler;
import org.yomirein.sochatserver.calls.CallHandler;
import org.yomirein.sochatserver.calls.CallService;
import org.yomirein.sochatserver.calls.p2p.P2PRoom;
import org.yomirein.sochatserver.chats.ChatHandler;
import org.yomirein.sochatserver.common.models.MessagePacket;
import org.yomirein.sochatserver.friendship.FriendsHandler;
import org.yomirein.sochatserver.messages.MessageHandler;
import org.yomirein.sochatserver.search.SearchHandler;
import org.yomirein.sochatserver.sessions.Session;
import org.yomirein.sochatserver.sessions.SessionManager;
import org.yomirein.sochatserver.users.UsersHandler;
import org.yomirein.sochatserver.utils.JwtService;
import static org.yomirein.sochatserver.utils.MessageSender.sendError;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.RequiredArgsConstructor;

// WsPacketHandler.java handles everything except authentication xd
@RequiredArgsConstructor
public class WsPacketHandler extends SimpleChannelInboundHandler<MessagePacket> {

    Logger LOGGER = LoggerFactory.getLogger(this.getClass());

    private final SessionManager sessionManager;

    private final AuthHandler authHandler;
    private final FriendsHandler friendsHandler;
    private final UsersHandler usersHandler;
    private final ChatHandler chatHandler;
    private final MessageHandler messageHandler;
    private final CallHandler callHandler;
    private final SearchHandler searchHandler;

    private final CallService callService;

    // Handling every packet, they separated by their appointment
    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, MessagePacket messagePacket) throws Exception {
        switch (messagePacket.getType()) {
            // ping-pong and authentication without withAuth
            // PING PONG
            // May be deleted later if I replace it with low-level ping-pong
            case "ping" -> ping(channelHandlerContext.channel());
            // Authentication
            case "authenticate" -> authHandler.authorize(channelHandlerContext, messagePacket);
            // FRIENDSHIP SERVICE
            case "friend_request" -> withAuth(channelHandlerContext, messagePacket, friendsHandler::requestSend);
            case "friend_accept" -> withAuth(channelHandlerContext, messagePacket, friendsHandler::requestAccept);
            case "friend_remove" -> withAuth(channelHandlerContext, messagePacket, friendsHandler::removeFriend);
            case "block" -> withAuth(channelHandlerContext, messagePacket, friendsHandler::blockUser);
            case "friend_decline" -> withAuth(channelHandlerContext, messagePacket, friendsHandler::requestDecline);
            case "relatives_list" -> withAuth(channelHandlerContext, messagePacket, friendsHandler::getRelatives);
            // USER SERVICE
            case "user_get" -> withAuth(channelHandlerContext, messagePacket, usersHandler::getUser);
            case "user_update_profile" -> withAuth(channelHandlerContext, messagePacket, usersHandler::changeProfile);
            // CHAT MANAGEMENT
            case "chat_create" -> withAuth(channelHandlerContext, messagePacket, chatHandler::createChat);
            case "chat_list" -> withAuth(channelHandlerContext, messagePacket, chatHandler::getUserChats);
            case "chat_get" -> withAuth(channelHandlerContext, messagePacket, chatHandler::getChat);
            case "chat_delete" -> withAuth(channelHandlerContext, messagePacket, chatHandler::deleteChat);
            case "chat_leave" -> withAuth(channelHandlerContext, messagePacket, chatHandler::removeParticipant);
            case "chat_get_users" -> withAuth(channelHandlerContext, messagePacket, chatHandler::getChatUsers);
            case "chat_add_participant" -> withAuth(channelHandlerContext, messagePacket, chatHandler::addParticipant);
            // MESSAGE MANAGEMENT
            case "message_send" -> withAuth(channelHandlerContext, messagePacket, messageHandler::sendMessage);
            case "message_edit" -> withAuth(channelHandlerContext, messagePacket, messageHandler::editMessage);
            case "message_delete" -> withAuth(channelHandlerContext, messagePacket, messageHandler::deleteMessage);
            case "message_read" -> {
                withAuth(channelHandlerContext, messagePacket, messageHandler::setLastReadMessage); System.out.println("t");
            }
            case "message_list" -> withAuth(channelHandlerContext, messagePacket, messageHandler::getRecentMessages);
            case "message_get" -> withAuth(channelHandlerContext, messagePacket, messageHandler::getMessage);
            case "turn_credentials_get" -> withAuth(channelHandlerContext, messagePacket, callHandler::turnCredentials);

            // CALLS MANAGEMENT
            case "call_offer" -> withAuth(channelHandlerContext, messagePacket, callHandler::call);
          //case "call_accept" -> withAuth(channelHandlerContext, messagePacket, callHandler::acceptCall);
            case "call_check" -> withAuth(channelHandlerContext, messagePacket, callHandler::checkCall);
            case "call_answer" -> withAuth(channelHandlerContext, messagePacket, callHandler::answerRtc);
            case "call_ice" -> withAuth(channelHandlerContext, messagePacket, callHandler::iceRtc);
            case "call_end" -> withAuth(channelHandlerContext, messagePacket, callHandler::endCall);
            // SEARCH
            case "search_user" -> withAuth(channelHandlerContext, messagePacket, searchHandler::searchUsers);

        }
            }

    // Ping Pong answer to user
    public void ping(Channel channel) {
        MessagePacket packetMessage = new MessagePacket("pong");
        packetMessage.payload.put("success", true);

        channel.writeAndFlush(packetMessage);
    }

    // Check authentication within handling almost every packet
    private void withAuth(ChannelHandlerContext ctx, MessagePacket messagePacket, AuthenticatedHandler handler) throws Exception {
        Long userId = null;

        if (sessionManager.isAuthenticated(ctx.channel())) {
            Session session = sessionManager.getSession(ctx.channel());
            if (!JwtService.isTokenValid(session.getToken())) {
                sendError(ctx, messagePacket, "invalid_token");
                sessionManager.removeSession(ctx.channel());
                return;
            }
            userId = session.getUser().getId();
        }

        handler.handle(ctx, messagePacket, userId);
    }


    // Interface to easier handling every incoming packet
    @FunctionalInterface
    private interface AuthenticatedHandler {
        void handle(ChannelHandlerContext ctx, MessagePacket messagePacket, Long userId) throws Exception;
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        LOGGER.trace("INACTIVE CTX {}", ctx.channel().id());
        Session currentSession = sessionManager.getSession(ctx.channel());

        if (currentSession != null) {
            Optional<P2PRoom> roomOpt = callService.findRoomBySession(currentSession);
            roomOpt.ifPresent(callService::deleteRoom);

            sessionManager.removeSession(ctx.channel());
        }

        super.channelInactive(ctx);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        LOGGER.trace("ACTIVE CTX {}", ctx.channel().id());
    }

    // Exceptions
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOGGER.error("Caught an exception", cause);
    }

}
