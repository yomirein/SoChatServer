package org.yomirein.sochatserver.common.repos;

import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.yomirein.sochatserver.database.Database;

public class TrustKeysRepository {

    public List<String> getEncryptedKeysByUserId(long userId) {
        String sql = "SELECT ARRAY_AGG(fingerprint) as fingerprints " +
                "FROM trust_keys " +
                "WHERE user_id = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);

            List<String> out = new ArrayList<>();

            try (ResultSet rs = ps.executeQuery()) {
                Array sqlArray = rs.getArray("fingerprints");
                if (sqlArray != null) {
                    String[] arr = (String[]) sqlArray.getArray();
                    out.addAll(Arrays.asList(arr));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

    }

    public Optional<String> getEncryptedKeyByUserIds(long fnOwnerId, long userId) {
        String sql = "SELECT fingerprint " +
                "FROM trust_keys " +
                "WHERE user_id = ? and fn_owner_id = ? " +
                "LIMIT 1";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setLong(2, fnOwnerId);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(rs.getString("fingerprint"));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }


    public boolean addKeyToUser(long userId, long ownerId, String key){
        String sql = "INSERT INTO trust_keys(user_id, fn_owner_id, fingerprint) VALUES (?, ?, ?)";

        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setLong(1, userId);
            ps.setLong(2, ownerId);
            ps.setString(3, key);

            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean removeKeyFromUser(long userId, long ownerId){
        String sql = "DELETE FROM trust_keys WHERE user_id = ? AND fn_owner_id = ?";

        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setLong(1, userId);
            ps.setLong(2, ownerId);

            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }


}
