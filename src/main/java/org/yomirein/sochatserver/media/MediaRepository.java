package org.yomirein.sochatserver.media;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.yomirein.sochatserver.database.Database;


public class MediaRepository {
    public Optional<Media> findById(String id) {
        String sql = "SELECT * FROM media WHERE media_id = ?";

        try (Connection conn = Database.getConnection();
             PreparedStatement psSelect = conn.prepareStatement(sql)) {

            psSelect.setString(1, id);

            try (ResultSet rs = psSelect.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                else {
                    Media media = new Media(
                            rs.getString("media_id"),
                            rs.getLong("message_id"),
                            rs.getLong("sender_id"),
                            rs.getString("mime_type"),
                            rs.getString("file_name"),
                            rs.getLong("file_size"),
                            rs.getObject("width", Integer.class),
                            rs.getObject("height", Integer.class),
                            rs.getObject("length", Integer.class),
                            rs.getString("nonce")
                    );
                    return Optional.of(media);
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean deleteById(String id) {
        String sql = "DELETE FROM media WHERE media_id = ?";

        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Media> findAttachedMessage(long messageId) {
        String sql = "SELECT * FROM media WHERE message_id = ?";

        List<Media> out = new ArrayList<>();

        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setLong(1, messageId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Media media = new Media(rs.getString("media_id"),
                            rs.getLong("message_id"),
                            rs.getLong("sender_id"),
                            rs.getString("mime_type"),
                            rs.getString("file_name"),
                            rs.getLong("file_size"),
                            rs.getObject("width", Integer.class),
                            rs.getObject("height", Integer.class),
                            rs.getObject("length", Integer.class),
                            rs.getString("nonce")
                    );

                    out.add(media);
                }
            }
            return out;

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean update(String mediaId, Long message_id, Integer width, Integer height, Integer length) {
        String sql = "UPDATE media SET message_id = COALESCE(?, message_id), width = COALESCE(?, width), height = COALESCE(?, height), length = COALESCE(?, length) WHERE media_id = ?";

        try (Connection c = Database.getConnection()) {
            try (PreparedStatement psUpdate = c.prepareStatement(sql)) {
                psUpdate.setObject(1, message_id);
                psUpdate.setObject(2, width);
                psUpdate.setObject(3, height);
                psUpdate.setObject(4, length);
                psUpdate.setObject(5, mediaId);

                return psUpdate.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Media save(String mediaId, long userId, String mimeType, String fileName, long fileSize, String nonce) {
        String sql = "INSERT INTO media(media_id, sender_id, mime_type, file_name, file_size, nonce) VALUES(?,?,?,?,?,?)";

        try (Connection c = Database.getConnection()) {
            try (PreparedStatement psInsert = c.prepareStatement(sql)) {
                psInsert.setString(1, mediaId);
                psInsert.setLong(2, userId);
                psInsert.setString(3, mimeType);
                psInsert.setString(4, fileName);
                psInsert.setLong(5, fileSize);
                psInsert.setString(6, nonce);

                psInsert.executeUpdate();
            }
            return new Media(
                    mediaId, null, userId,
                    mimeType, fileName,
                    fileSize,
                    null, null, null, nonce);

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
