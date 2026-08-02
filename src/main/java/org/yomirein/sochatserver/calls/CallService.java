package org.yomirein.sochatserver.calls;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.yomirein.sochatserver.calls.p2p.P2PRoom;
import org.yomirein.sochatserver.sessions.Session;
import org.yomirein.sochatserver.sessions.SessionManager;
import org.yomirein.sochatserver.users.User;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CallService {

    private final SessionManager sessionManager;
    Map<Long, P2PRoom> callRooms = new HashMap<>();

    public void offer(long chatId, Session session, String offerSdp) {
        P2PRoom p2pRoom = new P2PRoom(chatId, session);
        p2pRoom.setOfferSdp(offerSdp);

        callRooms.put(chatId, p2pRoom);
    }

    public void answer(long chatId, Session session) {
        P2PRoom p2pRoom = callRooms.get(chatId);
        p2pRoom.setSession2(session);
    }

    public void deleteRoom(long chatId) {
        System.out.println("Room deleted"); callRooms.remove(chatId); }
    public void deleteRoom(P2PRoom p2pRoom) {
        System.out.println("Room deleted");  callRooms.entrySet().removeIf(entry -> entry.getValue().equals(p2pRoom)); }

    public Optional<P2PRoom> findRoomBySession(Session session) throws Exception {
        if (session == null) {
            throw new Exception("Session is null");
        }

        for (P2PRoom room : callRooms.values()) {
            if (room.getSession1() == session ||
                    room.getSession2() == session) {
                return Optional.of(room);
            }
        }

        return Optional.empty();
    }

    public Optional<P2PRoom> findRoomByUser(User user) {
        Set<Session> userSessions = sessionManager.getUserSessions(user);
        for (P2PRoom room : callRooms.values()) {
           if (userSessions.contains(room.getSession1()) || (
               room.getSession2() != null && userSessions.contains(room.getSession2()))) {
               return Optional.of(room);
           }
        }

        return Optional.empty();
    }

    public Optional<P2PRoom> findRoomByChatId(long chatId) {
        if (callRooms.containsKey(chatId)){
            return Optional.of(callRooms.get(chatId));
        }
        return Optional.empty();
    }

    public boolean isChatInCall(long chatId) { return callRooms.containsKey(chatId); }
}
