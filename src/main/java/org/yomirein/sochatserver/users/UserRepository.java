package org.yomirein.sochatserver.users;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import org.yomirein.sochatserver.Database;
import static org.yomirein.sochatserver.utils.JsonConfig.mapUser;

// UserRepository.java, as like other repositories using for talking with database
public class UserRepository {

    private static final String USER_FIELDS =
        "id, nickname, username, description, ed25519_public_key, x25519_public_key";

    public User saveUser(User user) {
        String sql =
            "INSERT INTO users(nickname, username, ed25519_public_key, x25519_public_key) VALUES (?, ?, ?, ?) RETURNING id";
        try (
            Connection dbConnection = Database.getConnection();
            PreparedStatement ps = dbConnection.prepareStatement(sql)
        ) {
            ps.setString(1, user.getNickname());
            ps.setString(2, user.getUsername());
            ps.setString(
                3,
                Base64.getEncoder().encodeToString(
                    user.getEd25519PublicKey().getEncoded()
                )
            );
            ps.setString(
                4,
                Base64.getEncoder().encodeToString(
                    user.getX25519PublicKey().getEncoded()
                )
            );

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    user.setId(rs.getInt("id"));
                    return user;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public Optional<User> findByName(String username) {
        String sql = "SELECT " + USER_FIELDS + " FROM users WHERE username = ?";
        try (
            Connection conn = Database.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setString(1, username);
            return executeUserQuery(ps);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Optional<User> findById(Long id) {
        String sql = "SELECT " + USER_FIELDS + " FROM users WHERE id = ?";

        try (
            Connection conn = Database.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setLong(1, id);
            return executeUserQuery(ps);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<User> searchByUsername(String username, int offset, int limit) {
        List<User> out = new ArrayList<>();

        if (username.isEmpty() || username == null) {
            return out;
        }

        String sql =
            "SELECT " +
            USER_FIELDS +
            " FROM users WHERE username ILIKE ? ORDER BY id DESC OFFSET ? LIMIT ?";

        try (
            Connection c = Database.getConnection();
            PreparedStatement ps = c.prepareStatement(sql)
        ) {
            ps.setString(1, username + "%");
            ps.setLong(2, offset);
            ps.setLong(3, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(mapUser(rs));
            }
            return out;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean updateUser(
        Long id,
        String username,
        String nickname,
        String description
    ) {
        String sql =
            "UPDATE users SET username = COALESCE(?, username), nickname = ?, description = ? WHERE id = ?";
        try (
            Connection c = Database.getConnection();
            PreparedStatement ps = c.prepareStatement(sql)
        ) {
            ps.setString(1, username);
            ps.setString(2, nickname);
            ps.setString(3, description);
            ps.setLong(4, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // For easier mapping
    private Optional<User> executeUserQuery(PreparedStatement ps)
        throws SQLException {
        try (ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) return Optional.empty();
            return Optional.of(mapUser(rs));
        }
    }
}
