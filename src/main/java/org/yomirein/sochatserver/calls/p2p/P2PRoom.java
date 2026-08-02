package org.yomirein.sochatserver.calls.p2p;

import lombok.Getter;
import lombok.Setter;

import org.yomirein.sochatserver.sessions.Session;

import java.util.ArrayList;
import java.util.List;

import org.yomirein.sochatserver.chats.CallState;
@Getter @Setter
public class P2PRoom {
    private long chatId;

    private Session session1;
    private Session session2;

    private String offerSdp;

    private List<IceCandidatePayload> callerIce = new ArrayList<>();
    private List<IceCandidatePayload> calleeIce = new ArrayList<>();

    private CallState callState = CallState.IDLE;

    public P2PRoom(long chatId, Session session1) {
        this.chatId = chatId;
        this.session1 = session1;
        this.callState = CallState.CALLING;
    }

    public Session getOther(Session session) {
        if (session == session1) return session2;
        if (session == session2) return session1;
        return null;
    }

}
