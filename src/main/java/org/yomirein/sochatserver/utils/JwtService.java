package org.yomirein.sochatserver.utils;


import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.yomirein.sochatserver.calls.TurnCredential;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

public class JwtService {

    // Actually really simple JWT Service
    // It's so simple I just copypasted from previous try then I made sochat in Spring

    public static final String SECRET;
    private static final Key SIGNING_KEY;

    // Different security key so every reload every session become invalid
    // I guess it needed because users don't need to trust server owner
    static {
        byte[] keyBytes = new byte[32];
        new java.security.SecureRandom().nextBytes(keyBytes);
        SECRET = Base64.getEncoder().encodeToString(keyBytes);
        SIGNING_KEY = Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET));
    }


    public static String generateToken(String subject, JwtType type, int minutes) {
        Date exp = new Date(System.currentTimeMillis() + 1000L * 60 * minutes);
        return generateToken(subject, exp, type, new HashMap<>());
    }
    public static String generateToken(String subject, int minutes, JwtType type, Map<String, Object> values) {
        Date exp = new Date(System.currentTimeMillis() + 1000L * 60 * minutes);
        return generateToken(subject, exp, type, values);
    }

    // Main JWT Generation
    public static String generateToken(String subject, Date expirationDate, JwtType type, Map<String, Object> values) {
        Date now = new Date();
        if (type != null) values.put("type", type.toString());
        return Jwts.builder()
                .setClaims(values)
                .setSubject(subject)
                .setIssuedAt(now)
                .setExpiration(expirationDate)
                .signWith(SIGNING_KEY, SignatureAlgorithm.HS256)
                .compact();
    }

    // Parse token
    private static Claims parseClaims(String token) throws JwtException {
        return Jwts.parserBuilder()
                .setSigningKey(SIGNING_KEY)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    // Extract user username from token so we can understand who is token owner
    public static String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public static <T> T extractClaim(String token, Function<Claims, T> resolver) {
        Claims claims = parseClaims(token);
        return resolver.apply(claims);
    }

    // Check if token even valid
    public static boolean isTokenValid(String token) {
        try {
            Claims claims = parseClaims(token);
            Date expiration = claims.getExpiration();
            return expiration == null || expiration.after(new Date());
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    public static TurnCredential generateTurnCredential(long userId) throws NoSuchAlgorithmException, InvalidKeyException {
        long currentTime = System.currentTimeMillis() / 1000;
        long expireTime = currentTime + 3600;

        String turnUsername = expireTime + ":" + userId;

        SecretKeySpec signingKey = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA1");
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(signingKey);

        byte[] rawMac = mac.doFinal(turnUsername.getBytes(StandardCharsets.UTF_8));

        String turnCredential = Base64.getEncoder().encodeToString(rawMac);

        return new TurnCredential(turnUsername, turnCredential);
    }
}