package org.yomirein.sochatserver.chats;

import com.fasterxml.jackson.core.type.TypeReference;

import io.netty.channel.ChannelHandlerContext;
import lombok.RequiredArgsConstructor;

import org.yomirein.sochatserver.calls.CallService;
import org.yomirein.sochatserver.calls.p2p.P2PRoom;
import org.yomirein.sochatserver.common.models.MessagePacket;
import org.yomirein.sochatserver.messages.MessageService;
import org.yomirein.sochatserver.sessions.SessionManager;
import org.yomirein.sochatserver.users.User;
import org.yomirein.sochatserver.users.UserService;
import org.yomirein.sochatserver.utils.JsonConfig;
import org.yomirein.sochatserver.utils.MessageSender;

import java.util.*;

import static org.yomirein.sochatserver.utils.MessageSender.*;

import com.fasterxml.jackson.core.JsonProcessingException;

@RequiredArgsConstructor
public class ChatHandler {

    private final ChatService chatService;
    private final UserService userService;
    private final MessageService messageService;
    private final CallService callService;

    private final SessionManager sessionManager;

    public void getChat(ChannelHandlerContext channelHandlerContext, MessagePacket messagePacket, Long userId) {
        try {
            Chat chat;
            User fromUser = userService.getUser(userId);

            if (messagePacket.getPayload().hasNonNull("participant_username")) {
                String username = messagePacket.getPayload().get("participant_username").asText();
                User toUser = userService.getUser(username);

                chat = chatService.getChatByUsers(fromUser.getId(), toUser.getId());
            } else if (messagePacket.getPayload().hasNonNull("id")) {
                long id = messagePacket.getPayload().get("id").asLong();
                chat = chatService.getChat(id);
            } else {
                throw new IllegalArgumentException("Neither participant username nor chat id present in payload");
            }

            chat.setParticipants(chatService.getParticipantList(chat.getId()));
            chat.setSenderKeys(chatService.getUserSenderKeysByChat(chat.getId(), fromUser.getId()));

            Optional<P2PRoom> callRoom = callService.findRoomByChatId(chat.getId());
            if (callRoom.isPresent() && callRoom.get().getCallState() != CallState.IDLE) {
                chat.setCallState(callRoom.get().getCallState());
            }

            mapPrivateChat(chat, fromUser.getId(), chatService.getParticipantList(chat.getId()));
            MessagePacket answerPacket = buildBaseResponse(messagePacket, "Chat got successfully")
                    .put("chat", JsonConfig.MAPPER.writeValueAsString(chat))
                    .build();

            channelHandlerContext.channel().writeAndFlush(answerPacket);

        } catch (JsonProcessingException | IllegalArgumentException e) {
            sendError(channelHandlerContext, messagePacket, e.getMessage());
        }
    }


    public void getUserChats(ChannelHandlerContext channelHandlerContext, MessagePacket messagePacket, Long userId){
        try {
            List<Chat> chats = chatService.getUserChats(userId);

            chats.forEach(chat -> {
                List<Participant> participants = chatService.getParticipantList(chat.getId());

                Optional<P2PRoom> callRoom = callService.findRoomByChatId(chat.getId());
                if (callRoom.isPresent() && callRoom.get().getCallState() != CallState.IDLE) {
                    chat.setCallState((CallState)callRoom.get().getCallState());
                }

                chat.setParticipants(participants);
                if (chat.getChatType() == ChatType.PRIVATE) {
                    mapPrivateChat(chat, userId, participants);
                }
                chat.setLastSenderKey(chatService.getLastUserSenderKeyByChat(chat.getId(), userId));
                chat.setLastMessage(messageService.getLastMessage(chat.getId()));
            });


            MessagePacket answerPacket = buildBaseResponse(messagePacket, "Got chats lists")
                    .put("chats", JsonConfig.MAPPER.writeValueAsString(chats))
                    .build();

            channelHandlerContext.channel().writeAndFlush(answerPacket);
        } catch (JsonProcessingException e) {
            sendError(channelHandlerContext, messagePacket, e.getMessage());
        }
    }


    public void createChat(ChannelHandlerContext channelHandlerContext, MessagePacket messagePacket, Long userId) {
        try {

            String title = messagePacket.getPayload().get("title").asText(null);
            ChatType chatType = ChatType.valueOf(messagePacket.getPayload().get("chatType").asText());

            Map<Long, String> users = JsonConfig.MAPPER.convertValue(
                    messagePacket.getPayload().get("users"),
                    new TypeReference<Map<Long, String>>() {}
            );

            User fromUser = userService.getUser(userId);
            String fromEncryptedKey = messagePacket.getPayload().get("fromEncryptedKey").asText();

            Chat chat = chatService.createChat(title, chatType, users, userId, fromEncryptedKey);
            List<User> chatMembers = chat.getParticipants().stream()
                    .map(p -> userService.getUser(p.getUserId()))
                    .filter(user -> user.getId() != userId)
                    .toList();

            List<Participant> participants = chat.getParticipants();

            mapPrivateChat(chat, fromUser.getId(), participants);
            MessagePacket answerPacket = buildBaseResponse(messagePacket, "Chat created successfully")
                    .put("chat", JsonConfig.MAPPER.writeValueAsString(chat))
                    .build();

            for (User user : chatMembers){
                mapPrivateChat(chat, user.getId(), participants);
                MessagePacket requestInformation = new MessagePacket.Builder()
                        .type(messagePacket.getType())
                        .put("server_message", "Chat with user created")
                        .put("chat", JsonConfig.MAPPER.writeValueAsString(chat))
                        .build();
                notifyUser(user, requestInformation, sessionManager);
            }
            notifyUser(fromUser, answerPacket, sessionManager);

        } catch (JsonProcessingException | IllegalArgumentException e) {
            sendError(channelHandlerContext, messagePacket, e.getMessage());
        }
    }

    public void addParticipant(ChannelHandlerContext channelHandlerContext, MessagePacket messagePacket, Long userId) {
        try {

            User fromUser = userService.getUser(userId);
            User toUser = userService.getUser(messagePacket.getPayload().get("userId").asLong());

            long chatId = messagePacket.getPayload().get("chatId").asLong();
            Chat chat = chatService.getChat(chatId);


            if (chat.getChatType() == ChatType.PRIVATE){
                MessageSender.sendError(channelHandlerContext, messagePacket, "You can't add participant to private chat!");
                return;
            }

            chatService.addParticipant(chatId,toUser.getId());
            chat.setParticipants(chatService.getParticipantList(chat.getId()));

            Map<Long, String> users = JsonConfig.MAPPER.convertValue(
                    messagePacket.getPayload().get("users"),
                    new TypeReference<Map<Long, String>>() {}
            );
            int keyVersion = 0;

            switch (chat.getChatType()){
                case GROUP_SECURE ->  {
                    keyVersion = chatService.getCurrentKeyVersion(chatId)+1;
                }
                case GROUP_INSECURE ->  {
                    keyVersion = chatService.getCurrentKeyVersion(chatId);
                }
            }
            for (Map.Entry<Long, String> user : users.entrySet()){
                chatService.addSenderKey(chatId, user.getKey(), keyVersion, user.getValue());
            }

            List<User> chatMembers = chat.getParticipants().stream()
                    .map(p -> userService.getUser(p.getUserId()))
                    .filter(user -> user.getId() != userId)
                    .toList();


            List<Participant> participants = chat.getParticipants();

            mapPrivateChat(chat, fromUser.getId(), participants);
            chat.setSenderKeys(chatService.getUserSenderKeysByChat(chat.getId(), fromUser.getId()));
            MessagePacket answerPacket = buildBaseResponse(messagePacket, "User added to chat successfully")
                    .put("chat", JsonConfig.MAPPER.writeValueAsString(chat))
                    .build();
            notifyUser(fromUser, answerPacket, sessionManager);


            for (User user : chatMembers){
                mapPrivateChat(chat, user.getId(), participants);
                chat.setSenderKeys(chatService.getUserSenderKeysByChat(chat.getId(), user.getId()));
                MessagePacket requestInformation = new MessagePacket.Builder()
                        .type(messagePacket.getType())
                        .put("server_message", "User added to chat")
                        .put("chat", JsonConfig.MAPPER.writeValueAsString(chat))
                        .build();
                notifyUser(user, requestInformation, sessionManager);
            }


        } catch (JsonProcessingException | IllegalArgumentException e) {
            sendError(channelHandlerContext, messagePacket, e.getMessage());
        }
    }


    public void deleteChat(ChannelHandlerContext channelHandlerContext, MessagePacket messagePacket, Long userId){
        try {
            long chatId = messagePacket.getPayload().get("id").asLong();
            User fromUser = userService.getUser(userId);

            Chat chat = chatService.getChat(chatId);

            chat.setParticipants(chatService.getParticipantList(chat.getId()));
            List<User> chatMembers = chat.getParticipants().stream()
                    .map(p -> userService.getUser(p.getUserId()))
                    .filter(user -> user.getId() != userId)
                    .toList();

            List<Participant> participants = chatService.getParticipantList(chat.getId());

            chatService.deleteChat(chat);

            chat = mapPrivateChat(chat, fromUser.getId(), participants);
            MessagePacket answerPacket = buildBaseResponse(messagePacket, "Chat deleted successfully")
                    .put("chat", JsonConfig.MAPPER.writeValueAsString(chat))
                    .build();


            notifyUser(fromUser, answerPacket, sessionManager);

            for (User user : chatMembers){
                chat = mapPrivateChat(chat, user.getId(), participants);
                MessagePacket requestInformation = new MessagePacket.Builder()
                        .type(messagePacket.getType())
                        .put("server_message", "Chat deleted")
                        .put("chat", JsonConfig.MAPPER.writeValueAsString(chat))
                        .build();

                notifyUser(user, requestInformation, sessionManager);
            }

        } catch (JsonProcessingException e) {
            sendError(channelHandlerContext, messagePacket, e.getMessage());
        }
    }

    public void removeParticipant(ChannelHandlerContext channelHandlerContext, MessagePacket messagePacket, Long userId) {
        try {

            User fromUser = userService.getUser(userId);
            User toUser = userService.getUser(messagePacket.getPayload().get("userId").asLong());

            long chatId = messagePacket.getPayload().get("chatId").asLong();
            Chat chat = chatService.getChat(chatId);


            if (fromUser.getId() != toUser.getId() && chatService.getParticipant(chatId, fromUser.getId()).getChatRole() == ChatRole.MEMBER){
                MessageSender.sendError(channelHandlerContext, messagePacket, "You can't remove yourself from private chat!");
                return;
            }
            else if (chat.getChatType() == ChatType.PRIVATE){
                MessageSender.sendError(channelHandlerContext, messagePacket, "You can't remove yourself from private chat!");
                return;
            }
            else if (chatService.getParticipant(chatId, toUser.getId()).getChatRole() == ChatRole.OWNER){
                MessageSender.sendError(channelHandlerContext, messagePacket, "You remove chat owner from private chat!");
                return;
            }

            chatService.removeParticipant(chatId,toUser.getId());
            chatService.removeSenderKeys(chatId,toUser.getId());
            chat.setParticipants(chatService.getParticipantList(chat.getId()));

            List<User> chatMembers = chat.getParticipants().stream()
                    .map(p -> userService.getUser(p.getUserId()))
                    .filter(user -> user.getId() != userId)
                    .toList();


            List<Participant> participants = chat.getParticipants();

            mapPrivateChat(chat, fromUser.getId(), participants);
            chat.setSenderKeys(chatService.getUserSenderKeysByChat(chat.getId(), fromUser.getId()));
            MessagePacket answerPacket = buildBaseResponse(messagePacket, "User removed from chat successfully")
                    .put("chat", JsonConfig.MAPPER.writeValueAsString(chat))
                    .build();
            notifyUser(fromUser, answerPacket, sessionManager);


            for (User user : chatMembers){
                mapPrivateChat(chat, user.getId(), participants);
                chat.setSenderKeys(chatService.getUserSenderKeysByChat(chat.getId(), user.getId()));
                MessagePacket requestInformation = buildBaseResponse(messagePacket, "User removed from chat")
                        .put("chat", JsonConfig.MAPPER.writeValueAsString(chat))
                        .build();
                notifyUser(user, requestInformation, sessionManager);
            }


        } catch (JsonProcessingException e) {
            sendError(channelHandlerContext, messagePacket, e.getMessage());
        }
    }


    public void getChatUsers(ChannelHandlerContext channelHandlerContext, MessagePacket messagePacket, Long userId) {
        try {
            long id = messagePacket.getPayload().get("chatId").asLong();
            List<User> users = chatService.getChatUsers(id);

            MessagePacket answerPacket = buildBaseResponse(messagePacket, "Got successfully")
                    .put("users", JsonConfig.MAPPER.writeValueAsString(users))
                    .build();

            channelHandlerContext.channel().writeAndFlush(answerPacket);

        } catch (JsonProcessingException e) {
            sendError(channelHandlerContext, messagePacket, e.getMessage());
        }
    }




    private Chat mapPrivateChat(Chat chat, long senderId, List<Participant> participants){
        if (chat.getChatType() != ChatType.PRIVATE){
            return chat;
        }

        participants.stream()
                .filter(p -> p.getUserId() != senderId)
                .findFirst()
                .ifPresent(p -> chat.setTitle(userService.getUser(p.getUserId()).getNickname()));
        return chat;
    }
}
