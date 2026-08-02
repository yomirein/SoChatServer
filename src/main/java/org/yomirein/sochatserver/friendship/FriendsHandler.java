package org.yomirein.sochatserver.friendship;

import java.util.List;
import java.util.Optional;

import org.yomirein.sochatserver.common.models.MessagePacket;
import org.yomirein.sochatserver.sessions.SessionManager;
import org.yomirein.sochatserver.users.User;
import org.yomirein.sochatserver.users.UserService;
import org.yomirein.sochatserver.utils.JsonConfig;
import static org.yomirein.sochatserver.utils.MessageSender.buildBaseResponse;
import static org.yomirein.sochatserver.utils.MessageSender.notifyUser;
import static org.yomirein.sochatserver.utils.MessageSender.sendError;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.netty.channel.ChannelHandlerContext;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class FriendsHandler {

    private final SessionManager sessionManager;
    
    private final FriendshipRepository friendshipRepository;

    private final FriendshipService friendshipService;
    private final UserService userService;

    public void requestSend(ChannelHandlerContext channelHandlerContext, MessagePacket messagePacket, Long userId) {
        try {
            User toUser = userService.getUser(messagePacket.getPayload().get("username").asText());
            User user = userService.getUser(userId);

            if (toUser == user){
                MessagePacket answerPacket = new MessagePacket.Builder()
                        .type(messagePacket.getType())
                        .put("success", false)
                        .put("requestId", messagePacket.getPayload().get("requestId").asText())
                        .put("server_message", "You can't add yourself to friends")
                        .build();
                channelHandlerContext.channel().writeAndFlush(answerPacket);
                return;
            }

            String fingerprint = messagePacket.getPayload().get("fingerprint").asText();

            Friendship result = friendshipService.sendRequest(userId, toUser.getId(), fingerprint);

            MessagePacket answerPacket = buildBaseResponse(messagePacket, "Send friend request successfully")
                    .put("friendship", JsonConfig.MAPPER.writeValueAsString(result))
                    .build();

            MessagePacket requestInformation = new MessagePacket.Builder()
                    .type(messagePacket.getType())
                    .put("server_message", "New friend request")
                    .put("friendship", JsonConfig.MAPPER.writeValueAsString(result))
                    .build();

            notifyUser(toUser, requestInformation, sessionManager);
            channelHandlerContext.channel().writeAndFlush(answerPacket);

        } catch (JsonProcessingException e) {
            sendError(channelHandlerContext, messagePacket, e.getMessage());
        }
    }

    public void requestAccept(ChannelHandlerContext channelHandlerContext, MessagePacket messagePacket, Long userId){
        try {
            String fingerprint = messagePacket.getPayload().get("fingerprint").asText();

            User toUser = userService.getUser(messagePacket.getPayload().get("username").asText());
            User user = userService.getUser(userId);

            Optional<Friendship> friendshipCheck = friendshipRepository.findByUserAndFriend(user, toUser);
            if (friendshipCheck.isEmpty()){
                return;
            }
            Friendship friendship = friendshipCheck.get();

            if (friendship.getUser().getId() == user.getId()){
                MessagePacket answerPacket = buildBaseResponse(messagePacket,"You can't add friend by yourself!")
                        .build();
                channelHandlerContext.channel().writeAndFlush(answerPacket);
                return;
            }

            Friendship result = friendshipService.acceptRequest(friendship.getId(), fingerprint);

            MessagePacket answerPacket = buildBaseResponse(messagePacket,"Friend added successfully")
                    .put("friendship", JsonConfig.MAPPER.writeValueAsString(result))
                    .build();

            MessagePacket requestInformation = new MessagePacket.Builder()
                    .type(messagePacket.getType())
                    .put("server_message", "Friend added successfully")
                    .put("friendship", JsonConfig.MAPPER.writeValueAsString(result))
                    .build();

            notifyUser(toUser, requestInformation, sessionManager);
            channelHandlerContext.channel().writeAndFlush(answerPacket);

        } catch (JsonProcessingException e) {
            sendError(channelHandlerContext, messagePacket, e.getMessage());
        }
    }

    public void requestDecline(ChannelHandlerContext channelHandlerContext, MessagePacket messagePacket, Long userId){
        try {
            User toUser = userService.getUser(messagePacket.getPayload().get("username").asText());
            User user = userService.getUser(userId);

            Optional<Friendship> friendshipCheck = friendshipRepository.findByUserAndFriend(user, toUser);
            if (friendshipCheck.isEmpty()){
                return;
            }
            
            friendshipService.declineRequest(friendship.getId());

            MessagePacket answerPacket = buildBaseResponse(messagePacket,"Friend request declined successfully")
                    .put("removed", JsonConfig.MAPPER.writeValueAsString(toUser))
                    .put("user", JsonConfig.MAPPER.writeValueAsString(user))
                    .build();

            MessagePacket requestInformation = new MessagePacket.Builder()
                    .type(messagePacket.getType())
                    .put("server_message", "Friend request declined")
                    .put("removed", JsonConfig.MAPPER.writeValueAsString(toUser))
                    .put("user", JsonConfig.MAPPER.writeValueAsString(user))
                    .build();

            notifyUser(toUser, requestInformation, sessionManager);
            channelHandlerContext.channel().writeAndFlush(answerPacket);

        } catch (JsonProcessingException e) {
            sendError(channelHandlerContext, messagePacket, e.getMessage());
        }
    }

    public void removeFriend(ChannelHandlerContext channelHandlerContext, MessagePacket messagePacket, Long userId){
        try {
            User toUser = userService.getUser(messagePacket.getPayload().get("username").asText());
            User user = userService.getUser(userId);

            Optional<Friendship> friendshipCheck = friendshipRepository.findByUserAndFriend(user, toUser);
            if (friendshipCheck.isEmpty()){
                return;
            }
            Friendship friendship = friendshipCheck.get();

            friendshipService.removeFriendship(friendship.getId());

            MessagePacket answerPacket = buildBaseResponse(messagePacket,"Friendship deleted successfully")
                    .put("removed", JsonConfig.MAPPER.writeValueAsString(user))
                    .put("user", JsonConfig.MAPPER.writeValueAsString(toUser))
                    .build();

            MessagePacket requestInformation = new MessagePacket.Builder()
                    .type(messagePacket.getType())
                    .put("server_message", "Friendship deleted")
                    .put("removed", JsonConfig.MAPPER.writeValueAsString(user))
                    .put("user", JsonConfig.MAPPER.writeValueAsString(toUser))
                    .build();

            notifyUser(toUser, requestInformation, sessionManager);
            channelHandlerContext.channel().writeAndFlush(answerPacket);

        } catch (JsonProcessingException e) {
            sendError(channelHandlerContext, messagePacket, e.getMessage());
        }
    }


    public void blockUser(ChannelHandlerContext channelHandlerContext, MessagePacket messagePacket, Long userId){
        try {
            User blocked = userService.getUser(messagePacket.getPayload().get("username").asText());

            Friendship result = friendshipService.block(userId, blocked.getId());

            MessagePacket answerPacket = buildBaseResponse(messagePacket,"Blocked successfully")
                    .put("friendship", JsonConfig.MAPPER.writeValueAsString(result))
                    .build();

            MessagePacket requestInformation = new MessagePacket.Builder()
                    .type(messagePacket.getType())
                    .put("server_message", "You got blocked")
                    .put("friendship", JsonConfig.MAPPER.writeValueAsString(result))
                    .build();


            notifyUser(blocked, requestInformation, sessionManager);
            channelHandlerContext.channel().writeAndFlush(answerPacket);
        } catch (JsonProcessingException e) {
            sendError(channelHandlerContext, messagePacket, e.getMessage());
        }
    }

    public void getRelatives(ChannelHandlerContext channelHandlerContext, MessagePacket messagePacket, Long userId){
        try {
            List<Friendship> friendships = friendshipService.listByUser(userId);
            MessagePacket friendshipList = buildBaseResponse(messagePacket,"Got friendships list")
                    .put("friendship_list", JsonConfig.MAPPER.writeValueAsString(friendships))
                    .build();
            channelHandlerContext.channel().writeAndFlush(friendshipList);

        } catch (JsonProcessingException e) {
            sendError(channelHandlerContext, messagePacket, e.getMessage());
        }
    }

}
