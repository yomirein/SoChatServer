package org.yomirein.sochatserver.messages;


import java.util.List;

import org.yomirein.sochatserver.chats.Chat;
import org.yomirein.sochatserver.chats.ChatRole;
import org.yomirein.sochatserver.chats.ChatService;
import org.yomirein.sochatserver.chats.Participant;
import org.yomirein.sochatserver.common.models.MessagePacket;
import org.yomirein.sochatserver.media.MediaService;
import org.yomirein.sochatserver.sessions.SessionManager;
import org.yomirein.sochatserver.users.User;
import org.yomirein.sochatserver.users.UserService;
import org.yomirein.sochatserver.utils.JsonConfig;
import static org.yomirein.sochatserver.utils.JsonConfig.MAPPER;
import static org.yomirein.sochatserver.utils.JsonConfig.getIntOrNull;
import static org.yomirein.sochatserver.utils.JsonConfig.getLongOrNull;
import static org.yomirein.sochatserver.utils.JsonConfig.getTextOrNull;
import static org.yomirein.sochatserver.utils.MessageSender.buildBaseResponse;
import static org.yomirein.sochatserver.utils.MessageSender.handleError;
import static org.yomirein.sochatserver.utils.MessageSender.notifyUser;
import static org.yomirein.sochatserver.utils.MessageSender.sendError;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;

import io.netty.channel.ChannelHandlerContext;
import lombok.RequiredArgsConstructor;

// MessageHandler.java handles messages, getting them setting or deleting
@RequiredArgsConstructor
public class MessageHandler {

    // MessageService for easier contact with MessageRepository
    // ChatService for getting message chats
    // UserService for getting message senders
    // SessionManager to get all active sessions to send changes
    private final MessageService messageService;
    private final ChatService chatService;
    private final UserService userService;
    private final MediaService mediaService;

    private final SessionManager sessionManager;


    // Send message
    public void sendMessage(ChannelHandlerContext channelHandlerContext, MessagePacket messagePacket, Long userId) {
        try {
            // Get payload because I will use it multiple times
            JsonNode payload = messagePacket.getPayload();

            // Getting message content, chatId and reply message if it has it
            String content = getTextOrNull(payload, "content");
            Long chatId = getLongOrNull(payload, "chatId");
            Long replyMessageId = getLongOrNull(payload, "replyMessageId");
            List<String> mediaIds = MAPPER.readValue(
                    payload.get("media_files").asText(),
                    new TypeReference<List<String>>() {}
            );

            // If there's no content or chatId send error because it mandatory data
            if (content == null || chatId == null) {
                sendError(channelHandlerContext, messagePacket, "Not enough data");
                return;
            }

            // Get chat and its participants list
            Chat chat = chatService.getChat(chatId);
            chat.setParticipants(chatService.getParticipantList(chatId));

            // Remove sender from members list so we can send them other messages
            List<User> chatMembers = chat.getParticipants().stream()
                    .map(p -> userService.getUser(p.getUserId()))
                    .filter(messageSender -> messageSender.getId() != userId)
                    .toList();

            // Actually adding message to list
            Message message = messageService.addMessage(userId, chatId, content, replyMessageId, chatService.getCurrentKeyVersion(chatId), mediaIds);
            message.setMediaFiles(
                    mediaService.getAllMediaFromMessage(message.getId())
            );

            // Generating answer
            MessagePacket answerPacket = buildBaseResponse(messagePacket, "Message sent successfully")
                    .put("message", JsonConfig.MAPPER.writeValueAsString(message))
                    .build();
            MessagePacket requestInformation = new MessagePacket.Builder()
                    .type(messagePacket.getType())
                    .put("server_message", "You got message")
                    .put("message", JsonConfig.MAPPER.writeValueAsString(message))
                    .build();

            // Sending data to users
            notifyUser(userService.getUser(userId), answerPacket, sessionManager);
            for (User member : chatMembers) {
                notifyUser(member, requestInformation, sessionManager);
            }
        } catch (JsonProcessingException e) {
            handleError(channelHandlerContext, messagePacket, e);
        }
    }

    public void getMessage(ChannelHandlerContext channelHandlerContext, MessagePacket messagePacket, Long userId) {
        try {
            // Get messageId and if it's null send error and return
            Long messageId = getLongOrNull(messagePacket.getPayload(), "messageId");
            if (messageId == null) {
                sendError(channelHandlerContext, messagePacket, "Not enough data");
                return;
            }

            // Get message and send it
            Message message = messageService.getMessage(messageId);
            MessagePacket answerPacket = buildBaseResponse(messagePacket, "Got message successfully")
                    .put("message", JsonConfig.MAPPER.writeValueAsString(message))
                    .build();

            // Sending data
            channelHandlerContext.writeAndFlush(answerPacket);
        } catch (JsonProcessingException e) {
            handleError(channelHandlerContext, messagePacket, e);
        }
    }

    public void getRecentMessages(ChannelHandlerContext channelHandlerContext, MessagePacket messagePacket, Long userId){
        try {
            // Get offset and chatId and if they are null send error
            JsonNode payload = messagePacket.getPayload();
            Integer offset = getIntOrNull(payload, "offset");
            Long chatId = getLongOrNull(payload, "chatId");
            if (offset == null || chatId == null) {
                sendError(channelHandlerContext, messagePacket, "Not enough data");
                return;
            }
            // Get recent messages
            List<Message> messages = messageService.getRecentMessages(chatId, offset);
            for (Message message : messages) {
                message.setMediaFiles(
                        mediaService.getAllMediaFromMessage(message.getId())
                );
            }

            // Generate answer with messages
            MessagePacket answerPacket = buildBaseResponse(messagePacket, "Got messages successfully")
                    .put("messages", JsonConfig.MAPPER.writeValueAsString(messages))
                    .build();

            // Sending data
            channelHandlerContext.channel().writeAndFlush(answerPacket);
        } catch (JsonProcessingException e) {
            handleError(channelHandlerContext, messagePacket, e);
        }
    }

    public void setLastReadMessage(ChannelHandlerContext channelHandlerContext, MessagePacket messagePacket, Long userId) {
        try {
            // Get last read message id and if null send error
            Long messageId = getLongOrNull(messagePacket.getPayload(), "id");
            if (messageId == null) {
                sendError(channelHandlerContext, messagePacket, "Not enough data");
                return;
            }

            // Get message
            Message message = messageService.getMessage(messageId);

            Chat chat = chatService.getChat(message.getChatId());
            chat.setParticipants(chatService.getParticipantList(message.getChatId()));

            // Get chat members without reader to send different packets
            List<User> chatMembers = chat.getParticipants().stream()
                    .map(p -> userService.getUser(p.getUserId()))
                    .filter(user -> user.getId() != userId)
                    .toList();

            // Check if message reader in chat
            Participant participant = chat.getParticipants().stream().filter(u -> u.getUserId() == userId).findFirst().orElse(null);
            if (participant == null) {
                sendError(channelHandlerContext, messagePacket, "User does not contains in chat");
                return;
            }
            // Setting data
            participant.setLastMessageId(messageId);
            Participant result = messageService.setLastReadMessage(participant);

            MessagePacket answerPacket = buildBaseResponse(messagePacket, "You read message successfully")
                    .put("participant", JsonConfig.MAPPER.writeValueAsString(result))
                    .build();

            MessagePacket requestInformation = new MessagePacket.Builder()
                    .type(messagePacket.getType())
                    .put("server_message", "User read message successfully")
                    .put("participant", JsonConfig.MAPPER.writeValueAsString(result))
                    .build();

            // Sending data to users
            notifyUser(userService.getUser(userId), answerPacket, sessionManager);
            for (User member : chatMembers) {
                notifyUser(member, requestInformation, sessionManager);
            }
        } catch (JsonProcessingException e) {
            handleError(channelHandlerContext, messagePacket, e);
        }
    }


    public void editMessage(ChannelHandlerContext channelHandlerContext, MessagePacket messagePacket, Long userId){
        try {
            // Get payload because we will use it multiple times
            JsonNode payload = messagePacket.getPayload();

            // Getting new content and messageId
            String content = getTextOrNull(payload, "content");
            Long messageId = getLongOrNull(payload, "id");

            // If content or messageId is null send error because it mandatory data
            if (content == null || messageId == null) {
                sendError(channelHandlerContext, messagePacket, "Not enough data");
                return;
            }

            // Get old message
            Message oldMessage = messageService.getMessage(messageId);

            // Get chat and its participants list
            Chat chat = chatService.getChat(oldMessage.getChatId());
            chat.setParticipants(chatService.getParticipantList(oldMessage.getChatId()));

            // Check if user is message sender
            if (oldMessage.getSenderId() != userId){
                sendError(channelHandlerContext, messagePacket, "You can't edit others user message!");
                return;
            }

            // Get chat members without editor to send them update
            List<User> chatMembers = chat.getParticipants().stream()
                    .map(p -> userService.getUser(p.getUserId()))
                    .filter(user -> user.getId() != userId)
                    .toList();

            // Actually editing message
            Message message = messageService.editMessage(messageId, content);

            // Generating answer
            MessagePacket answerPacket = buildBaseResponse(messagePacket, "Message edited successfully")
                    .put("message", JsonConfig.MAPPER.writeValueAsString(message))
                    .build();

            MessagePacket requestInformation = new MessagePacket.Builder()
                    .type(messagePacket.getType())
                    .put("server_message", "User edited message")
                    .put("message", JsonConfig.MAPPER.writeValueAsString(message))
                    .build();

            // Sending data to users
            notifyUser(userService.getUser(userId), answerPacket, sessionManager);
            for (User member : chatMembers) {
                notifyUser(member, requestInformation, sessionManager);
            }
        } catch (JsonProcessingException e) {
            handleError(channelHandlerContext, messagePacket, e);
        }
    }

    public void deleteMessage(ChannelHandlerContext channelHandlerContext, MessagePacket messagePacket, Long userId){
        try {
            // Get messageId and if it's null send error
            Long messageId = getLongOrNull(messagePacket.getPayload(), "id");
            if (messageId == null) {
                sendError(channelHandlerContext, messagePacket, "Not enough data");
                return;
            }

            // Get message
            Message message = messageService.getMessage(messageId);

            // Get chat and its participants list
            Chat chat = chatService.getChat(message.getChatId());
            chat.setParticipants(chatService.getParticipantList(message.getChatId()));

            // Check if user can delete message
            // Only message sender itself or user with privileges in chat can do this
            boolean isOwnerMessage = message.getSenderId() == (userId);

            boolean isAdminOrOwner = chat.getParticipants().stream()
                    .filter(p -> p.getUserId() == (userId))
                    .anyMatch(p -> p.getChatRole() == ChatRole.ADMIN || p.getChatRole() == ChatRole.OWNER);

            if (!isOwnerMessage && !isAdminOrOwner) {
                sendError(channelHandlerContext, messagePacket, "You can't delete other users' messages!");
                return;
            }

            // Get chat members without deleter to send them update
            List<User> chatMembers = chat.getParticipants().stream()
                    .map(p -> userService.getUser(p.getUserId()))
                    .filter(user -> user.getId() != userId)
                    .toList();

            // Actually deleting message
            messageService.deleteMessage(messageId);

            // Generating answer
            MessagePacket answerPacket = buildBaseResponse(messagePacket, "Message deleted successfully")
                    .put("message", JsonConfig.MAPPER.writeValueAsString(message))
                    .build();

            MessagePacket requestInformation = new MessagePacket.Builder()
                    .type(messagePacket.getType())
                    .put("server_message", "User deleted message")
                    .put("message", JsonConfig.MAPPER.writeValueAsString(message))
                    .build();

            // Sending data to users
            notifyUser(userService.getUser(userId), answerPacket, sessionManager);
            for (User member : chatMembers) {
                notifyUser(member, requestInformation, sessionManager);
            }
        } catch (JsonProcessingException e) {
            handleError(channelHandlerContext, messagePacket, e);
        }
    }

}
