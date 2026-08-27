package org.yomirein.sochatserver.friendship;

import org.yomirein.sochatserver.database.Database;
import org.yomirein.sochatserver.users.User;
import org.yomirein.sochatserver.users.UserRepository;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class FriendshipRepository {

    private final UserRepository userRepo = new UserRepository();

    public List<Friendship> findByUserOrFriend(User user, User friend) {
        String sql = "SELECT id, user_id, friend_id, status FROM friendship WHERE user_id = ? OR friend_id = ?";
        List<Friendship> out = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, user.getId());
            ps.setLong(2, friend.getId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(mapFriendship(rs));
            }
            return out;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Optional<Friendship> findByUserAndFriend(User user, User friend) {
        String sql = "SELECT id, user_id, friend_id, status " +
                " FROM friendship " +
                " WHERE (user_id = ? AND friend_id = ?) " +
                "   OR (user_id = ? AND friend_id = ?) " +
                " LIMIT 1 ";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, user.getId());
            ps.setLong(2, friend.getId());
            ps.setLong(3, friend.getId());
            ps.setLong(4, user.getId());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(mapFriendship(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Optional<Friendship> findById(Long id) {
        String sql = "SELECT id, user_id, friend_id, status FROM friendship WHERE id = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();

                Friendship f = new Friendship();
                f.setId(rs.getLong("id"));
                Long userId = rs.getLong("user_id");
                Long friendId = rs.getLong("friend_id");
                userRepo.findById(userId).ifPresent(f::setUser);
                userRepo.findById(friendId).ifPresent(f::setFriend);
                f.setStatus(Enum.valueOf(FriendshipStatus.class, rs.getString("status")));
                return Optional.of(f);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Friendship> findByUserAndStatus(User user, FriendshipStatus status) {
        String sql = "SELECT id, user_id, friend_id, status FROM friendship WHERE user_id = ? AND status = ?";
        List<Friendship> out = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, user.getId());
            ps.setString(2, status.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(mapFriendship(rs));
            }
            return out;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Friendship> findByFriendAndStatus(User friend, FriendshipStatus status) {
        String sql = "SELECT id, user_id, friend_id, status FROM friendship WHERE friend_id = ? AND status = ?";
        List<Friendship> out = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, friend.getId());
            ps.setString(2, status.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(mapFriendship(rs));
            }
            return out;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }


    public Friendship saveOrUpdate(Friendship f) {
        String updateSql = "UPDATE friendship SET status = ? WHERE user_id = ? AND friend_id = ?";
        String insertSql = "INSERT INTO friendship(user_id, friend_id, status) VALUES (?, ?, ?) RETURNING id";

        try (Connection c = Database.getConnection()) {
            try (PreparedStatement psUpdate = c.prepareStatement(updateSql)) {
                psUpdate.setString(1, f.getStatus().name());
                psUpdate.setLong(2, f.getUser().getId());
                psUpdate.setLong(3, f.getFriend().getId());
                int updated = psUpdate.executeUpdate();

                if (updated > 0) {
                    return f;
                }
            }
            try (PreparedStatement psInsert = c.prepareStatement(insertSql)) {
                psInsert.setLong(1, f.getUser().getId());
                psInsert.setLong(2, f.getFriend().getId());
                psInsert.setString(3, f.getStatus().name());

                try (ResultSet rs = psInsert.executeQuery()) {
                    if (rs.next()) f.setId(rs.getLong(1));
                }
            }

            return f;

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }


    public boolean deleteById(long friendshipId) {
        String sql = "DELETE FROM friendship WHERE id = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, friendshipId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private Friendship mapFriendship(ResultSet rs) throws SQLException {
        Friendship f = new Friendship();
        f.setId(rs.getLong("id"));
        long uid = rs.getLong("user_id");
        long fid = rs.getLong("friend_id");
        userRepo.findById(uid).ifPresent(f::setUser);
        userRepo.findById(fid).ifPresent(f::setFriend);
        f.setStatus(FriendshipStatus.valueOf(rs.getString("status")));
        return f;
    }
}
