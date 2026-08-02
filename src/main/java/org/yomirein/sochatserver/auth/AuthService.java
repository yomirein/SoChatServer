package org.yomirein.sochatserver.auth;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

import org.yomirein.sochatserver.common.managers.ChallengeManager;
import org.yomirein.sochatserver.common.models.Challenge;
import org.yomirein.sochatserver.common.models.MessagePacket;
import org.yomirein.sochatserver.users.User;
import org.yomirein.sochatserver.users.UserRepository;
import org.yomirein.sochatserver.utils.JwtService;
import org.yomirein.sochatserver.utils.JwtType;
import org.yomirein.sochatserver.utils.KeyParser;

public class AuthService {

    private final ChallengeManager challengeManager;
    private final UserRepository userRepository;

    public AuthService(ChallengeManager challengeManager, UserRepository userRepository) {
        this.challengeManager = challengeManager;
        this.userRepository = userRepository;

        challengeManager.startCleanupThread();
    }

    public MessagePacket createChallenge(String username) {

        Optional<User> userCheck = userRepository.findByName(username);

        if (userCheck.isPresent()) {
            User user = userCheck.get();

            Challenge challenge = challengeManager.generateChallenge(user.getId());

            return new MessagePacket.Builder()
                    .type("challenge")
                    .put("success", true)
                    .put("challenge", challenge.getChallenge())
                    .put("expire_time", challenge.getExpireTime())
                    .put("server_message", "Signature challenge with your private key")
                    .build();
        }
        else{
            return authResponse("login", false, "User is not valid");
        }
    }

    public MessagePacket login(String username, String signature, String challenge) {

        Optional<User> userCheck = userRepository.findByName(username);

        if (userCheck.isPresent()) {

            User user = userCheck.get();

            if (!challengeManager.checkChallenge(challengeManager.getChallenges().get(user.getId()))){
                return authResponse("login", false, "Challenge is not valid");
            }

            var decodedPublicKey = Base64.getDecoder().decode(KeyParser.convertPublicKeyToString(user.getEd25519PublicKey()));
            try {
                if (!challengeManager.verifyChallenge(
                        challenge.getBytes(StandardCharsets.UTF_8),
                        Base64.getDecoder().decode(signature), decodedPublicKey)) {
                    return authResponse("login", false, "Challenge is not verified");
                }
                else{
                    return new MessagePacket.Builder()
                            .type("login")
                            .put("success", true)
                            .put("server_message", "Login success")
                            .put("token", JwtService.generateToken(user.getUsername(), JwtType.AUTH, 60 * 24))
                            .build();
                }


            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        else  {
            return authResponse("login", false, "User is not valid");
        }

    }

    // TODO: MAKE CHECK AUTHENTICATION FOR AUTHENTICATED ONLINE USERS
    public void checkAuthentication(String token){
        Optional<User> user = userRepository.findByName(JwtService.extractUsername(token));

        if (user.isEmpty()){
            return;
        }

        if (JwtService.isTokenValid(token)){

        }
        else {

        }

    }

    public MessagePacket register(String username, String ed25519PublicKey, String x25519PublicKey) {

        Optional<User> userCheck = userRepository.findByName(username);
        if (userCheck.isPresent()) {
            return authResponse("register", false, "User already exists");
        }

        try {
            User user = new User(username,
                    KeyParser.stringToPublicKeyED25519(ed25519PublicKey),
                    KeyParser.stringToPublicKeyX25519(x25519PublicKey));
            userRepository.saveUser(user);

            return authResponse("register", true, "User created successfully");


        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    private MessagePacket authResponse(String type, boolean success, String message) {
        return new MessagePacket.Builder()
                .type(type)
                .put("success", success)
                .put("server_message", message)
                .build();
    }

}
