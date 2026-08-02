package org.yomirein.sochatserver.users;

import java.security.PublicKey;

import org.yomirein.sochatserver.utils.PublicKeySerializer;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Getter @Setter
    private long id;

    @Setter
    private String nickname;

    @Getter @Setter
    private String username;

    @Getter @Setter
    private String description;

    @Getter @Setter
    @JsonSerialize(using = PublicKeySerializer.class)
    private PublicKey ed25519PublicKey;

    @Getter @Setter
    @JsonSerialize(using = PublicKeySerializer.class)
    private PublicKey x25519PublicKey;

    // Additional
    public User(String nickname, String username, String description, PublicKey ed25519PublicKey, PublicKey x25519PublicKey) {
        this.nickname = nickname;
        this.username = username;
        this.description = description;
        this.ed25519PublicKey = ed25519PublicKey;
        this.x25519PublicKey = x25519PublicKey;
    }
    public User(String username, String description, PublicKey ed25519PublicKey, PublicKey x25519PublicKey) {
        this.username = username;
        this.description = description;
        this.ed25519PublicKey = ed25519PublicKey;
        this.x25519PublicKey = x25519PublicKey;
    }
    public User(String username, PublicKey ed25519PublicKey, PublicKey x25519PublicKey) {
        this.username = username;
        this.ed25519PublicKey = ed25519PublicKey;
        this.x25519PublicKey = x25519PublicKey;
    }

    // Get username as nickname if it is null
    public String getNickname() {
        if (nickname == null) {
            return username;
        }
        return nickname;
    }
}
