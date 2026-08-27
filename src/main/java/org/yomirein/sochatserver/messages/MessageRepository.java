package org.yomirein.sochatserver.messages;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.yomirein.sochatserver.chats.Participant;
import org.yomirein.sochatserver.database.Database;

public class MessageRepository {

    public Optional<Message> findById(Long id) {
        String sql = "SELECT id, chat_id, sender_id, reply_message_id, content, timestamp, key_version FROM message WHERE id = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(mapMessage(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Optional<Message> findLastById(Long id) {
        String sql = "SELECT id, chat_id, sender_id, reply_message_id, content, timestamp, key_version FROM message WHERE chat_id = ? ORDER BY timestamp DESC LIMIT 1";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(mapMessage(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Message> findByChatId(Long chatId) {
        String sql = "SELECT id, chat_id, sender_id, reply_message_id, content, timestamp, key_version FROM message WHERE chat_id = ? ORDER BY timestamp ASC";
        List<Message> out = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, chatId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(mapMessage(rs));
            }
            return out;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean deleteById(long messageId) {
        String sql = "DELETE FROM message WHERE id = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, messageId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Message> findTop20ByChatIdOrderByTimestampDesc(Long chatId) {
        String sql = "SELECT id, chat_id, sender_id, reply_message_id, content, timestamp, key_version FROM message WHERE chat_id = ? ORDER BY timestamp DESC LIMIT 20";
        List<Message> out = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, chatId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(mapMessage(rs));
            }
            return out;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Message> findByChatIdOrderByTimestampDesc(Long chatId, int offset, int limit) {
        String sql = "SELECT id, chat_id, sender_id, reply_message_id, content, timestamp, key_version FROM message WHERE chat_id = ? ORDER BY timestamp DESC OFFSET ? LIMIT ?";
        List<Message> out = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, chatId);
            ps.setInt(2, offset);
            ps.setInt(3, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(mapMessage(rs));
            }
            return out;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Message> findUnreadByChatIdOrderByTimestampDesc(Long chatId) {
        String sql = "SELECT COUNT(m.*)" +
                "FROM message AS m " +
                "JOIN chat_participants AS p ON m.chat_id = p.chat_id AND p.user_id = 56 " +
                "WHERE m.chat_id = 1 AND m.id > p.last_read_message_id " +
                "ORDER BY m.timestamp DESC";
        List<Message> out = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, chatId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(mapMessage(rs));
            }
            return out;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }


    public Participant setReadLastMessage(Participant participant) {
        String sql = "UPDATE public.chat_participants SET last_read_message_id=? WHERE chat_id = ? AND user_id = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, participant.getLastMessageId());
            ps.setLong(2, participant.getChatId());
            ps.setLong(3, participant.getUserId());
            ps.executeUpdate();

            return participant;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }


    public Message save(Message m) {
        String sql = "INSERT INTO message(chat_id, sender_id, reply_message_id, content, timestamp, key_version) VALUES (?, ?, ?, ?, ?, ?) RETURNING id, timestamp";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, m.getChatId());
            ps.setLong(2, m.getSenderId());
            if (m.getReplyMessageId() != null) {
                ps.setLong(3, m.getReplyMessageId());
            } else {
                ps.setNull(3, Types.BIGINT);
            }
            ps.setString(4, m.getContent());

            Timestamp ts = m.getTimestamp() != null ? Timestamp.valueOf(m.getTimestamp()) : Timestamp.valueOf(LocalDateTime.now());

            ps.setTimestamp(5, ts);
            ps.setInt(6, m.getKeyVersion());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    m.setId(rs.getLong("id"));
                    Timestamp returned = rs.getTimestamp("timestamp");
                    if (returned != null) m.setTimestamp(returned.toLocalDateTime());
                }
            }
            return m;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Message update(Message m) {
        String sql = "UPDATE message SET content = ? WHERE id = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, m.getContent());
            ps.setLong(2, m.getId());
            ps.executeUpdate();

            return m;

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }


    private Message mapMessage(ResultSet rs) throws SQLException {
        Message m = new Message();
        m.setId(rs.getLong("id"));
        m.setChatId(rs.getLong("chat_id"));
        m.setSenderId(rs.getLong("sender_id"));

        m.setReplyMessageId(rs.getLong("reply_message_id"));
        m.setKeyVersion(rs.getInt("key_version"));

        m.setContent(rs.getString("content"));
        Timestamp ts = rs.getTimestamp("timestamp");
        if (ts != null) m.setTimestamp(ts.toLocalDateTime());
        return m;
    }
}
