package org.yomirein.sochatserver.search;

import java.util.List;

import org.yomirein.sochatserver.common.models.MessagePacket;
import org.yomirein.sochatserver.users.User;
import static org.yomirein.sochatserver.utils.JsonConfig.MAPPER;
import static org.yomirein.sochatserver.utils.JsonConfig.getIntOrNull;
import static org.yomirein.sochatserver.utils.JsonConfig.getTextOrNull;
import static org.yomirein.sochatserver.utils.MessageSender.buildBaseResponse;
import static org.yomirein.sochatserver.utils.MessageSender.handleError;

import io.netty.channel.ChannelHandlerContext;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SearchHandler {

    private final SearchService searchService;

    public void searchUsers(
        ChannelHandlerContext channelHandlerContext,
        MessagePacket messagePacket,
        Long userId
    ) {
        try {
            String username = getTextOrNull(
                messagePacket.getPayload(),
                "username"
            );
            if (username == null) {
                username = "";
            }
            Integer offset = getIntOrNull(messagePacket.getPayload(), "offset");
            if (offset == null) {
                offset = 0;
            }
            List<User> usersList = searchService.findByUsername(
                username,
                offset
            );

            usersList.removeIf(user -> user.getId() == userId);

            MessagePacket answerPacket = buildBaseResponse(
                messagePacket,
                "Got search users list successfully"
            )
                .put("users", MAPPER.writeValueAsString(usersList))
                .build();

            channelHandlerContext.channel().writeAndFlush(answerPacket);
        } catch (Exception e) {
            handleError(channelHandlerContext, messagePacket, e);
        }
    }
}
