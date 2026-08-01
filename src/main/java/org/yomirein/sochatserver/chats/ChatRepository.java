package org.yomirein.sochatserver.chats;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.yomirein.sochatserver.Database;
import org.yomirein.sochatserver.users.User;
import static org.yomirein.sochatserver.utils.JsonConfig.mapUser;

public class ChatRepository {

    public Optional<Chat> findById(Long id) {
        String sql = 
            """
            SELECT 
              c.id,
              c.type,
              c.title,
              (
                SELECT COUNT(*) 
                FROM message m 
                JOIN chat_participants p ON p.chat_id = m.chat_id 
                WHERE p.user_id = 56 
                  AND m.chat_id = c.id 
                  AND m.id > p.last_read_message_id
              ) AS unread_count 
            FROM chat c
            WHERE c.id = ?;
            """;
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();

                Chat chat = new Chat();
                chat.setId(rs.getLong("id"));
                chat.setChatType(ChatType.valueOf(rs.getString("type")));
                chat.setTitle(rs.getString("title"));

                return Optional.of(chat);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Optional<Chat> findChatByContainingMessageId(long messageId) {
        String sql = "SELECT c.* FROM chats c JOIN messages m ON m.chat_id = c.id WHERE m.id = ?;";

        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, messageId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();

                Chat chat = new Chat();
                chat.setId(rs.getLong("id"));
                chat.setChatType(ChatType.valueOf(rs.getString("type")));
                chat.setTitle(rs.getString("title"));

                return Optional.of(chat);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }


    public Optional<Chat> findByIdWithParticipants(Long chatId) {
        Optional<Chat> opt = findById(chatId);
        if (opt.isEmpty()) return Optional.empty();

        Chat chat = opt.get();
        chat.setParticipants(loadParticipants(chatId));
        return Optional.of(chat);
    }

    // TODO: MAKE GETTING CHATS WITH LAST MESSAGE AND LAST CHAT KEY
    public List<Chat> findAllByParticipantId(Long userId) {
        String sql = "SELECT c.id, c.type, c.title " +
                "FROM chat c JOIN chat_participants cp ON c.id = cp.chat_id " +
                "WHERE cp.user_id = ?";
        List<Chat> out = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Chat chat = new Chat();

                    chat.setId(rs.getLong("id"));
                    chat.setChatType(ChatType.valueOf(rs.getString("type")));
                    chat.setTitle(rs.getString("title"));

                    out.add(chat);
                }
            }
            return out;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Optional<Chat> findPrivateChatBetween(Long userId1, Long userId2) {
        String sql = """
            SELECT c.id, c.type, c.title
            FROM chat c
            JOIN chat_participants cp ON c.id = cp.chat_id
            GROUP BY c.id, c.type, c.title
            HAVING
                COUNT(*) = 2
                AND SUM(CASE WHEN cp.user_id = ? THEN 1 ELSE 0 END) > 0
                AND SUM(CASE WHEN cp.user_id = ? THEN 1 ELSE 0 END) > 0
            LIMIT 1
        """;
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId1);
            ps.setLong(2, userId2);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                Chat chat = new Chat();
                chat.setId(rs.getLong("id"));
                chat.setChatType(ChatType.valueOf(rs.getString("type")));
                chat.setTitle(rs.getString("title"));

                // I don't want loading everything
                //chat.setParticipants(loadParticipants(chat.getId()));
                //chat.setSenderKeys(findAllSenderKeyByChat(chat.getId()));
                return Optional.of(chat);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Participant> loadParticipants(Long chatId) {
        String sql = "SELECT chat_id, user_id, role, last_read_message_id FROM chat_participants WHERE chat_id = ?";
        List<Participant> participants = new ArrayList<>();

        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setLong(1, chatId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Participant participant = new Participant();
                    participant.setChatId(rs.getLong("chat_id"));
                    participant.setUserId(rs.getLong("user_id"));
                    participant.setChatRole(ChatRole.valueOf(rs.getString("role")));
                    participant.setLastMessageId(rs.getLong("last_read_message_id"));

                    participants.add(participant);
                }
            }

            return participants;

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public boolean addParticipant(Participant participant) {
        String sql = "INSERT INTO chat_participants (chat_id, user_id, role) VALUES (?, ?, ?)";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, participant.getChatId());
            ps.setLong(2, participant.getUserId());
            ps.setObject(3, participant.getChatRole().name(), java.sql.Types.OTHER);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean removeParticipant(long chat_id, long user_id) {
        String sql = "DELETE FROM chat_participants WHERE chat_id = ? AND user_id = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, chat_id);
            ps.setLong(2, user_id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }




    public boolean removeSenderKeys(long chatId, long userId){
        String sql = "DELETE FROM chat_sender_keys WHERE chat_id = ? AND user_id = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, chatId);
            ps.setLong(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }


    public Optional<Participant> getParticipantByUserIdAndChatId(Long userId, Long chatId) {
        String sql = "SELECT * from chat_participants WHERE user_id = ? AND chat_id = ? LIMIT 1";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setLong(1, userId);
            ps.setLong(2, chatId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                Participant participant = new Participant();
                participant.setChatId(rs.getLong("chat_id"));
                participant.setUserId(rs.getLong("user_id"));
                participant.setChatRole(ChatRole.valueOf(rs.getString("role")));
                return Optional.of(participant);
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public List<User> getUsersByChatId(Long chatId) {
        String sql = "SELECT u.* FROM chat_participants cp JOIN users u ON u.id = cp.user_id WHERE cp.chat_id = ?";
        List<User> users = new ArrayList<>();

        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setLong(1, chatId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    users.add(mapUser(rs));
                }
            }

            return users;

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    public boolean addSenderKey(SenderKey senderKey){
        String sql = "INSERT INTO chat_sender_keys (chat_id, user_id, key_version, chat_key) VALUES (?, ?, ?, ?)";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, senderKey.getChatId());
            ps.setLong(2, senderKey.getUserId());
            ps.setInt(3, senderKey.getKeyVersion());
            ps.setString(4, senderKey.getChatKey());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public int getCurrentKeyVersion(long chatId) {
        String sql = "SELECT MAX(key_version) FROM chat_sender_keys WHERE chat_id = ?";

        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, chatId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
                return 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Optional<SenderKey> findLastSenderKeyByChatAndUser(long chatId, long userId){
        String sql = 
            """
            SELECT chat_id, user_id, key_version, chat_key
            FROM chat_sender_keys
            WHERE chat_id = ? AND user_id = ?
            ORDER BY key_version DESC
            LIMIT 1
            """;

        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, chatId);
            ps.setLong(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                SenderKey senderKey = new SenderKey();
                senderKey.setChatId(rs.getLong("chat_id"));
                senderKey.setUserId(rs.getLong("user_id"));
                senderKey.setKeyVersion(rs.getInt("key_version"));
                senderKey.setChatKey(rs.getString("chat_key"));

                return Optional.of(senderKey);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<SenderKey> findAllSenderKeyByChatAndId(long chatId, long userId) {
        String sql =
            """
            SELECT chat_id, user_id, key_version, chat_key
            FROM chat_sender_keys
            WHERE chat_id = ? AND user_id = ?
            ORDER BY key_version DESC
            """;

        List<SenderKey> senderKeys = new ArrayList<>();

        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setLong(1, chatId);
            ps.setLong(2, userId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    SenderKey senderKey = new SenderKey();
                    senderKey.setChatId(rs.getLong("chat_id"));
                    senderKey.setUserId(rs.getLong("user_id"));
                    senderKey.setKeyVersion(rs.getInt("key_version"));
                    senderKey.setChatKey(rs.getString("chat_key"));

                    senderKeys.add(senderKey);
                }
            }
            return senderKeys;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public List<SenderKey> findAllSenderKeyByChat(long chatId) {
        String sql = "SELECT chat_id, user_id, key_version, chat_key FROM chat_sender_keys WHERE chat_id = ? ORDER BY key_version DESC";
        List<SenderKey> senderKeys = new ArrayList<>();

        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setLong(1, chatId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    SenderKey senderKey = new SenderKey();
                    senderKey.setChatId(rs.getLong("chat_id"));
                    senderKey.setUserId(rs.getLong("user_id"));
                    senderKey.setKeyVersion(rs.getInt("key_version"));
                    senderKey.setChatKey(rs.getString("chat_key"));

                    senderKeys.add(senderKey);
                }
            }
            return senderKeys;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    public Chat save(Chat chat) {
        String sql = "INSERT INTO chat (type, title) VALUES (?, ?) RETURNING id";

        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setObject(1, chat.getChatType().name(), java.sql.Types.OTHER);
            ps.setString(2, chat.getTitle());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    chat.setId(rs.getLong("id"));
                }
            }

            if (chat.getParticipants() != null) {
                for (Participant participant : chat.getParticipants()) {
                    participant.setChatId(chat.getId());
                    addParticipant(participant);

                }
                for (SenderKey senderKey : chat.getSenderKeys()){
                    senderKey.setChatId(chat.getId());
                    addSenderKey(senderKey);
                }
            }

            return chat;

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }




    public boolean deleteById(long chatId) {
        String sql = "DELETE FROM chat WHERE id = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, chatId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
