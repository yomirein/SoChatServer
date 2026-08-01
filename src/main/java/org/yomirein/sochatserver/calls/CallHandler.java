package org.yomirein.sochatserver.calls;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.yomirein.sochatserver.calls.p2p.IceCandidatePayload;
import org.yomirein.sochatserver.calls.p2p.P2PRoom;
import org.yomirein.sochatserver.chats.CallState;
import org.yomirein.sochatserver.chats.Chat;
import org.yomirein.sochatserver.chats.ChatService;
import org.yomirein.sochatserver.chats.ChatType;
import org.yomirein.sochatserver.chats.Participant;
import org.yomirein.sochatserver.common.models.MessagePacket;
import org.yomirein.sochatserver.friendship.FriendshipService;
import org.yomirein.sochatserver.sessions.Session;
import org.yomirein.sochatserver.sessions.SessionManager;
import org.yomirein.sochatserver.users.User;
import org.yomirein.sochatserver.users.UserService;
import org.yomirein.sochatserver.utils.ConfigReader;
import org.yomirein.sochatserver.utils.JwtService;
import org.yomirein.sochatserver.utils.MessageSender;
import static org.yomirein.sochatserver.utils.MessageSender.notifyUser;
import static org.yomirein.sochatserver.utils.MessageSender.sendError;

import io.netty.channel.ChannelHandlerContext;
import lombok.RequiredArgsConstructor;

/*
       Dope ### wizard tower on CallHandler
                    ^
                   | |
                -|^   ^|-
                |        |
              _|^        ^|_
              |____________|
                |       |
                |  ---- |
                |  |  | |
                | ------|
                |       |
                |       |
*/

@RequiredArgsConstructor
public class CallHandler {

    private final CallService callService;
    private final FriendshipService friendshipService;
    private final UserService userService;
    private final ChatService chatService;

    private final SessionManager sessionManager;

    // Getting turn credentials for calls
    public void turnCredentials(ChannelHandlerContext channelHandlerContext, MessagePacket messagePacket, Long userId) {
        try {
            // If user already in call prevent to give any turn credential
            // THIS MISCONCEPTION WON'T WORK, TODO: NEED TO COMPLETELY REWORK SESSIONS
            /*
            if (callService.findRoomBySession(sessionManager.getSession(channelHandlerContext.channel())).isPresent()) {
                MessagePacket messagePacket1 = MessageSender.buildBaseResponse(messagePacket, "Can't get turn credentials because user already have one").build();
                channelHandlerContext.channel().writeAndFlush(messagePacket1);
                return;
            }*/

            TurnCredential turnCredential = JwtService.generateTurnCredential(userId);
            MessagePacket answerPacket = MessageSender.buildBaseResponse(messagePacket, "Got turn credentials successfully")
                    .put("username", turnCredential.username)
                    .put("credential", turnCredential.credential)
                    .put("turn_ip", ConfigReader.getConfig().get("turn.realm"))
                    .put("turn_port", Integer.parseInt(ConfigReader.getConfig().get("turn.port")))
                    .build();
            channelHandlerContext.channel().writeAndFlush(answerPacket);
        } catch (NumberFormatException | InvalidKeyException | NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public void checkCall(ChannelHandlerContext channelHandlerContext, MessagePacket messagePacket, Long userId) {
        try {
            long chatId = messagePacket.getPayload().get("chat_id").asLong();
            long toId = messagePacket.getPayload().get("user_id").asLong();

            Chat chat = chatService.getChat(chatId);
            User fromUser = userService.getUser(userId);
            User toUser = userService.getUser(toId);

            if (chat.getChatType() != ChatType.PRIVATE) {
                MessageSender.sendError(channelHandlerContext, messagePacket, "You call in non private chat!");
                return;
            }

            if (!friendshipService.isFriends(userId, toUser.getId())) {
                MessageSender.sendError(channelHandlerContext, messagePacket, "You can't call people if they are not in your Friend list!");
                return;
            }


            Set<Session> toUserSessions = sessionManager.getUserSessions(toUser);
            if (toUserSessions.isEmpty()) {
                MessageSender.sendError(channelHandlerContext, messagePacket, "User is offline");
                return;
            }

            if (callService.findRoomByUser(fromUser).isEmpty()) {
                Optional<P2PRoom> p2pRoomOpt = callService.findRoomByUser(toUser);
                if (p2pRoomOpt.isPresent()) {
                    P2PRoom p2pRoom = p2pRoomOpt.get();

                    callService.answer(p2pRoom.getChatId(), sessionManager.getSession(channelHandlerContext.channel()));

                    MessagePacket offerPacket = MessageSender.buildBaseResponse(messagePacket, "P2PRoom already exists!")
                        .put("chat_id", p2pRoom.getChatId())
                        .put("sdp", p2pRoom.getOfferSdp()).build();
                    channelHandlerContext.channel().writeAndFlush(offerPacket);


                    for (IceCandidatePayload iceCandidatePayload : p2pRoom.getCallerIce()) {
                        MessagePacket icePacket = new MessagePacket.Builder()
                            .type("call_ice")
                            .put("candidate", iceCandidatePayload.getCandidate())
                            .put("sdp_mid", iceCandidatePayload.getSdpMid())
                            .put("sdp_mline_index", iceCandidatePayload.getSdpMLineIndex())
                            .build();
                        channelHandlerContext.channel().writeAndFlush(icePacket);
                    }

                } else {
                    MessagePacket messagePacket1 = MessageSender.buildBaseResponse(messagePacket, "P2PRoom does not exist, you can call")
                        .put("chat_id", chatId)
                        .build();
                    channelHandlerContext.channel().writeAndFlush(messagePacket1);
                }
            } else {
                sendError(channelHandlerContext, messagePacket, "You can't call user while you are already in call!");
            }
        } catch (Exception e) {
            sendError(channelHandlerContext, messagePacket, e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public void call(ChannelHandlerContext channelHandlerContext, MessagePacket messagePacket, Long userId) {
        try {
            long chatId = messagePacket.getPayload().get("chat_id").asLong();
            long toId = messagePacket.getPayload().get("user_id").asLong();

            User toUser = userService.getUser(toId);
            Chat chat = chatService.getChat(chatId);

            if (chat.getChatType() != ChatType.PRIVATE) {
                MessageSender.sendError(channelHandlerContext, messagePacket, "You call in non private chat!");
                return;
            }

            if (!friendshipService.isFriends(userId, toUser.getId())) {
                MessageSender.sendError(channelHandlerContext, messagePacket, "You can't call people if they are not in your Friend list!");
                return;
            }

            Set<Session> toUserSessions = sessionManager.getUserSessions(toUser);
            if (toUserSessions.isEmpty()) {
                MessageSender.sendError(channelHandlerContext, messagePacket, "User is offline");
                return;
            } String offer = messagePacket.getPayload().get("sdp").asText();

            callService.offer(chat.getId(), sessionManager.getSession(channelHandlerContext.channel()), offer);

            MessagePacket messagePacket2 = MessageSender.buildBaseResponse(
                    messagePacket, "Someone calling you").put("chat_id", chat.getId()).build();

            MessageSender.notifyUser(toUserSessions, messagePacket2);

        } catch (Exception e) {
            sendError(channelHandlerContext, messagePacket, e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public void answerRtc(ChannelHandlerContext channelHandlerContext, MessagePacket messagePacket, Long userId) {
        try {
            Session userSession = sessionManager.getSession(channelHandlerContext.channel());
            P2PRoom p2pRoom = callService.findRoomBySession(userSession).orElseThrow(
                    () -> new IllegalStateException("Session is not in any call!")
            );
            
            p2pRoom.setCallState(CallState.IN_CALL);
            
            Session otherSession = p2pRoom.getOther(userSession);

            String answer = messagePacket.getPayload().get("sdp").asText();

            MessagePacket answerPacket = new MessagePacket.Builder()
                    .type("call_answer")
                    .put("sdp", answer)
                    .put("chat_id", p2pRoom.getChatId())
                    .build();

            otherSession.getChannel().writeAndFlush(answerPacket);
        } catch (Exception e) {
            sendError(channelHandlerContext, messagePacket, e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public void iceRtc(ChannelHandlerContext channelHandlerContext, MessagePacket messagePacket, Long userId) {
        try {
            Session userSession = sessionManager.getSession(channelHandlerContext.channel());
            P2PRoom p2pRoom = callService.findRoomBySession(userSession).orElseThrow(
                    () -> new IllegalStateException("Session is not in any call!")
            );

            Session otherSession = p2pRoom.getOther(userSession);

            String candidate = messagePacket.getPayload().get("candidate").asText();
            String sdpMid = messagePacket.getPayload().get("sdp_mid").asText();
            int sdpMLineIndex = messagePacket.getPayload().get("sdp_mline_index").asInt();

            if (otherSession == null) {
                IceCandidatePayload iceCandidatePayload = new IceCandidatePayload(candidate, sdpMid, sdpMLineIndex);
                p2pRoom.getCallerIce().add(iceCandidatePayload);
            }
            else {
                MessagePacket icePacket = new MessagePacket.Builder()
                        .type("call_ice")
                        .put("candidate", candidate)
                        .put("sdp_mid", sdpMid)
                        .put("sdp_mline_index", sdpMLineIndex)
                        .build();

                otherSession.getChannel().writeAndFlush(icePacket);
            }

        } catch (Exception e) {
            sendError(channelHandlerContext, messagePacket, e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public void endCall(ChannelHandlerContext channelHandlerContext, MessagePacket messagePacket, Long userId) {
        try {
            Session userSession = sessionManager.getSession(channelHandlerContext.channel());
            P2PRoom p2pRoom = callService.findRoomBySession(userSession).orElseThrow(
                    () -> new IllegalStateException("Session is not in any call!")
            );

            Chat chat = chatService.getChat(p2pRoom.getChatId());
            List<Participant> participants = chatService.getParticipantList(chat.getId());
            callService.deleteRoom(chat.getId());

            MessagePacket endCallPacket = MessageSender.buildBaseResponse(messagePacket, "Call end")
                    .put("chat_id", chat.getId()).put("success", true).build();

            for (Participant participant : participants) {
                Set<Session> participantSessions = sessionManager.getUserSessions(participant.getUserId());
                notifyUser(participantSessions, endCallPacket);
            }
        } catch (Exception e) {
            sendError(channelHandlerContext, messagePacket, e.getMessage());
            throw new RuntimeException(e);
        }
    }



}
