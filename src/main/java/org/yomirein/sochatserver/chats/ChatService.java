package org.yomirein.sochatserver.chats;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.yomirein.sochatserver.users.User;
import org.yomirein.sochatserver.users.UserService;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ChatService {

    private final UserService userService;
    private final ChatRepository chatRepository;

    public Chat getChatByUsers(long userId1, long userId2){
        try{
            return chatRepository.findPrivateChatBetween(userId1, userId2).orElseThrow(() -> new RuntimeException("Chat not found"));
        }catch (RuntimeException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Chat> getUserChats(long userId){
        try {

            return chatRepository.findAllByParticipantId(userId);
        }catch (RuntimeException e) {
            throw new RuntimeException(e);
        }
    }

    public Chat getChat(long chatId){
        try{
            return chatRepository.findById(chatId).orElseThrow(() -> new RuntimeException("Chat not found"));
        }catch (RuntimeException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Participant> getParticipantList(long chatId){
        try{
            return chatRepository.loadParticipants(chatId);
        }catch (RuntimeException e) {
            throw new RuntimeException(e);
        }
    }

    public List<SenderKey> getChatSenderKeys(long chatId){
        try{
            return chatRepository.findAllSenderKeyByChat(chatId);
        }catch (RuntimeException e) {
            throw new RuntimeException(e);
        }
    }

    public List<SenderKey> getUserChatSenderKeys(long chatId, long userid){
        try{
            return chatRepository.findAllSenderKeyByChatAndId(chatId, userid);
        }catch (RuntimeException e) {
            throw new RuntimeException(e);
        }
    }

    public Participant getParticipant(long chatId, long userId){
        try{
            return chatRepository.getParticipantByUserIdAndChatId(userId, chatId).orElseThrow(() -> new RuntimeException("Participant not found"));
        }catch (RuntimeException e) {
            throw new RuntimeException(e);
        }
    }


    public SenderKey getLastUserSenderKeyByChat(long chatId, long userId){
        try{
            return chatRepository.findLastSenderKeyByChatAndUser(chatId, userId).orElseThrow(() -> new RuntimeException("Sender key not found"));
        }catch (RuntimeException e) {
            throw new RuntimeException(e);
        }
    }

        public List<SenderKey> getUserSenderKeysByChat(long chatId, long userId){
        try{
            return chatRepository.findAllSenderKeyByChat(chatId)
                    .stream()
                    .filter(c -> c.getUserId() == userId)
                    .toList();

        }catch (RuntimeException e) {
            throw new RuntimeException(e);
        }
    }

    public Chat createChat(String title, ChatType chatType, Map<Long, String> users, Long fromUserId, String fromEncryptedKey) {
        try {
            User from = userService.getUser(fromUserId);

            Chat chat = new Chat();
            chat.setChatType(chatType);
            chat.setTitle(title);

            List<Participant> participants = new ArrayList<>();
            List<SenderKey> senderKeys = new ArrayList<>();

            if (chatType == ChatType.PRIVATE) participants.add(new Participant(null, from.getId(), ChatRole.MEMBER));
            else participants.add(new Participant(null, from.getId(), ChatRole.OWNER));

            senderKeys.add(new SenderKey(null, from.getId(), 0, fromEncryptedKey));

            for (Map.Entry<Long, String> entry : users.entrySet()) {

                Long userId = entry.getKey();
                String encryptedKey = entry.getValue();

                userService.getUser(userId);

                participants.add(new Participant(null, userId, ChatRole.MEMBER));
                senderKeys.add(new SenderKey(null, userId, 0, encryptedKey));
            }
            chat.setParticipants(participants);
            chat.setSenderKeys(senderKeys);

            return chatRepository.save(chat);
        }catch (RuntimeException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean addParticipant(long chatId, long userId){
        try {
            Participant participant = new Participant(chatId, userId, ChatRole.MEMBER);

            return chatRepository.addParticipant(participant);
        }catch (RuntimeException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean removeParticipant(long chatId, long userId){
        try {
            return chatRepository.removeParticipant(chatId, userId);
        }catch (RuntimeException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean removeSenderKeys(long chatId, long userId){
        try {
            return chatRepository.removeSenderKeys(chatId, userId);
        }catch (RuntimeException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean addSenderKey(long chatId, long userId, int keyVersion, String chatKey){
        try {
            SenderKey senderKey = new SenderKey(chatId, userId, keyVersion, chatKey);

            return chatRepository.addSenderKey(senderKey);
        }catch (RuntimeException e) {
            throw new RuntimeException(e);
        }
    }

    public int getCurrentKeyVersion(long chatId){
        try{
            return chatRepository.getCurrentKeyVersion(chatId);
        } catch (RuntimeException e) {
            throw new RuntimeException(e);
        }
    }



    public boolean deleteChat(Chat chat){
        try {
            return chatRepository.deleteById(chat.getId());
        }catch (RuntimeException e) {
            throw new RuntimeException(e);
        }
    }

    public List<User> getChatUsers(long chatId){
        try{
            return chatRepository.getUsersByChatId(chatId);
        }catch (RuntimeException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean isUserInChat(long chatId, User user){
        List<Chat> chats = getUserChats(user.getId());
        return chats.stream().anyMatch(chat -> { return chat.getId() == chatId; } );
    }

    public Chat getChatByMessageId(long messageId) {
        try {
            return chatRepository.findChatByContainingMessageId(messageId).orElseThrow(() -> new RuntimeException("Chat not found"));
        } catch (RuntimeException e) {
            throw new RuntimeException(e);
        }
    }

    public Chat getChatByMessageId(long messageId, boolean withParticipants) {
        try {
            if (withParticipants) {
                Chat chat = chatRepository.findChatByContainingMessageId(messageId).orElseThrow(() -> new RuntimeException("Chat not found"));
                chat.setParticipants(getParticipantList(chat.getId()));
                return chat;
            }
            return chatRepository.findChatByContainingMessageId(messageId).orElseThrow(() -> new RuntimeException("Chat not found"));
        } catch (RuntimeException e) {
            throw new RuntimeException(e);
        }
    }
}
