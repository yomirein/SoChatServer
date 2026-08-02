package org.yomirein.sochatserver.chats;

import java.util.List;

import org.yomirein.sochatserver.messages.Message;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Chat {
    private long id;
    private String title;
    private ChatType chatType;
    private List<SenderKey> senderKeys;
    private List<Participant> participants;

    private Message lastMessage;
    private SenderKey lastSenderKey;
    private Integer unreadMessagesCount;

    private CallState callState = CallState.IDLE;
}
