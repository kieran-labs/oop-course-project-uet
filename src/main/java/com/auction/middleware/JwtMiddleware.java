package com.auction.middleware;

import com.auction.config.JwtUtil;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import io.javalin.http.Context;
import io.javalin.http.UnauthorizedResponse;

import java.util.List;

public class JwtMiddleware {

    private static final List<String> EXCLUDED_PATHS = List.of(
        "/api/auth/login",
        "/api/auth/register",
        "/api/health"
    );

    public static void handle(Context ctx) {
        String path = ctx.path();

        if (EXCLUDED_PATHS.contains(path)) {
            return;
        }

        String authHeader = ctx.header("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new UnauthorizedResponse("Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7);

        try {
            DecodedJWT decoded = JwtUtil.verifyToken(token);

            ctx.attribute("userId", decoded.getClaim("userId").asLong());
            ctx.attribute("username", decoded.getSubject());
            ctx.attribute("role", decoded.getClaim("role").asString());

        } catch (JWTVerificationException e) {
            throw new UnauthorizedResponse("Invalid or expired token");
        }
    }
}
