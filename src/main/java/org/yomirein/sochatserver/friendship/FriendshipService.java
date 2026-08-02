package org.yomirein.sochatserver.friendship;

import java.util.List;
import java.util.Optional;

import org.yomirein.sochatserver.common.repos.TrustKeysRepository;
import org.yomirein.sochatserver.users.User;
import org.yomirein.sochatserver.users.UserRepository;

import lombok.RequiredArgsConstructor;


@RequiredArgsConstructor
public class FriendshipService {

    // Friendships uses FriendshipStatus types
    // Types like PENDING, BLOCKED and ACCEPTED

    private final FriendshipRepository friendshipRepository;
    private final UserRepository userRepository;
    private final TrustKeysRepository trustKeysRepository;

    // Send friend request from user to user
    // Firstly it checks if user exists
    // Then it's creating Friendship with status and sets user fingerprint(to check if users valid)
    // and finally saves to repository
    public Friendship sendRequest(Long fromUserId, Long toUserId, String fingerprint) {
        try {
            User from = userRepository.findById(fromUserId).orElseThrow(() -> new RuntimeException("User not found"));
            User to = userRepository.findById(toUserId).orElseThrow(() -> new RuntimeException("User not found"));

            Optional<Friendship> existing = friendshipRepository.findByUserAndFriend(from, to);
            if (existing.isPresent()) return existing.get();

            Friendship f = new Friendship();
            f.setUser(from);
            f.setFriend(to);
            f.setStatus(FriendshipStatus.PENDING);

            trustKeysRepository.addKeyToUser(toUserId, fromUserId, fingerprint);

            return friendshipRepository.saveOrUpdate(f);
        }catch (RuntimeException e) {
            throw new RuntimeException(e);
        }
    }

    //
    //
    // Four next methods will check for Friendship existing
    //
    //

    // Decline friendship, it just deletes Friendship and users Trust Keys from Database
    public boolean declineRequest(Long requestId) {
        try {
            Optional<Friendship> opt = friendshipRepository.findById(requestId);
            if (opt.isEmpty()) {
                throw new RuntimeException("Friendship request not found: " + requestId);
            }
            Friendship f = opt.get();

            trustKeysRepository.removeKeyFromUser(f.getFriend().getId(), f.getUser().getId());

            return friendshipRepository.deleteById(f.getId());
        }catch (RuntimeException e) {
            throw new RuntimeException(e);
        }
    }

    // Removes friendship, it removes existing frindship nor previous
    public boolean removeFriendship(Long requestId) {
        try {
            Optional<Friendship> opt = friendshipRepository.findById(requestId);
            if (opt.isEmpty()) {
                throw new RuntimeException("Friendship not found: " + requestId);
            }
            Friendship f = opt.get();

            trustKeysRepository.removeKeyFromUser(f.getFriend().getId(), f.getUser().getId());
            trustKeysRepository.removeKeyFromUser(f.getUser().getId(), f.getFriend().getId());

            return friendshipRepository.deleteById(f.getId());
        }catch (RuntimeException e) {
            throw new RuntimeException(e);
        }
    }

    // Accepts friendship
    public Friendship acceptRequest(Long requestId, String fingerprint){
        try{
            Optional<Friendship> opt = friendshipRepository.findById(requestId);

            if (opt.isEmpty()) {
                throw new RuntimeException("Friendship not found: " + requestId);
            }

            Friendship f = opt.get();
            f.setStatus(FriendshipStatus.ACCEPTED);

            trustKeysRepository.addKeyToUser(f.getUser().getId(), f.getFriend().getId(), fingerprint);

            return friendshipRepository.saveOrUpdate(f);
        }catch (RuntimeException e) {
            throw new RuntimeException(e);
        }
    }

    // Blocks user, sets blocked state to Friendship
    public Friendship block(long userId, long blockedId){
        try{
            User from = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("user not found"));
            User to = userRepository.findById(blockedId).orElseThrow(() -> new RuntimeException("user not found"));

            Friendship f = new Friendship();
            f.setUser(from);
            f.setFriend(to);
            f.setStatus(FriendshipStatus.BLOCKED);

            trustKeysRepository.removeKeyFromUser(f.getUser().getId(), f.getFriend().getId());
            trustKeysRepository.removeKeyFromUser(f.getFriend().getId(), f.getUser().getId());

            return friendshipRepository.saveOrUpdate(f);
        }catch (RuntimeException e) {
            throw new RuntimeException(e);
        }
    }


    // Getting all friendships that user have
    public List<Friendship> listByUser(Long userId) {
        User u = userRepository.findById(userId).orElseThrow();
        return friendshipRepository.findByUserOrFriend(u, u);
    }

    public Boolean isFriends(long userId1, long userId2) {
        try {
            User u1 = userRepository.findById(userId1).orElseThrow();
            User u2 = userRepository.findById(userId2).orElseThrow();

            Optional<Friendship> friendshipOpt = friendshipRepository.findByUserAndFriend(u1, u2);
            if (friendshipOpt.isPresent()) return friendshipOpt.get().getStatus() == FriendshipStatus.ACCEPTED;
            else return false;
        }
        catch (RuntimeException e) {
            throw new RuntimeException(e);
        }
    }


}
