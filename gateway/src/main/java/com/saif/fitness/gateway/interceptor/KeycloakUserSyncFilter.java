package com.saif.fitness.gateway.interceptor;

import com.saif.fitness.gateway.user.UserRequestDto;
import com.saif.fitness.gateway.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.text.ParseException;

@Component
@Slf4j
@RequiredArgsConstructor
public class KeycloakUserSyncFilter implements WebFilter {

    private final UserService userService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {

        String path = exchange.getRequest().getURI().getPath();

        if (path.startsWith("/api/auth/")) {
            log.info("Skipping KeycloakUserSyncFilter for {}", path);
            return chain.filter(exchange);
        }

        log.info("Incoming request: {}",exchange.getRequest().getURI());
        log.info("calling user sync filter");
        String userId=exchange.getRequest().getHeaders().getFirst("X-User-ID");
        String token=exchange.getRequest().getHeaders().getFirst("Authorization");
        UserRequestDto userRequestDto=getUserDetails(token);
        if(userId == null){
            userId=userRequestDto.getKeycloakId();
        }
        if (userId != null && !token.isEmpty()){
            String finalUserId = userId;
            return userService.validateUser(userId)
                    .flatMap(exists -> {
                        if (!exists) {
                            if (userRequestDto != null) {
                                return userService.registerUser(userRequestDto)
                                        .then(Mono.just(false));
                            } else {
                                return Mono.just(false);
                            }
                        } else {
                            log.info("User already exist, Skipping sync");
                            return Mono.just(true);
                        }
                    })
                    .then(Mono.defer(() -> {
                        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                                .header("X-User-ID", finalUserId)
                                .build();
                        return chain.filter(exchange.mutate().request(mutatedRequest).build());
                    }));

        }
        return chain.filter(exchange);
    }

    private UserRequestDto getUserDetails(String token) {
        log.info("Parsing jwt token");
        try {
            String tokenWithoutBearer=token.replace("Bearer","").trim();
            SignedJWT signedJWT=SignedJWT.parse(tokenWithoutBearer);
            JWTClaimsSet claims=signedJWT.getJWTClaimsSet();

            UserRequestDto userRequestDto=new UserRequestDto();
            userRequestDto.setEmail(claims.getStringClaim("email"));
            userRequestDto.setKeycloakId(claims.getStringClaim("sub"));
            userRequestDto.setFirstName(claims.getStringClaim("given_name"));
            userRequestDto.setLastName(claims.getStringClaim("family_name"));

            return userRequestDto;
        }catch (ParseException e){
            throw new RuntimeException(e);
        }
    }
}