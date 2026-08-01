package org.yomirein.sochatserver.netty.handlers;

import java.util.List;
import java.util.Map;

import org.yomirein.sochatserver.auth.AuthService;
import org.yomirein.sochatserver.common.models.MessagePacket;
import org.yomirein.sochatserver.media.MediaHandler;
import org.yomirein.sochatserver.utils.JsonConfig;
import static org.yomirein.sochatserver.utils.MessageSender.sendHttp;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.CharsetUtil;
import lombok.AllArgsConstructor;

// HttpPacketHandler using for register, validate user and then authenticate user
@AllArgsConstructor
public class HttpPacketHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    private final AuthService authService;
    private final MediaHandler mediaHandler;


    // Read requests
    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, FullHttpRequest fullHttpRequest) throws Exception {

        String uri = fullHttpRequest.uri();
        // Basic answer if someone got into ./
        if ("/".equals(fullHttpRequest.uri())) {
            FullHttpResponse response = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            response.content().writeBytes("SoChat!".getBytes());
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
            channelHandlerContext.writeAndFlush(response);
        } else {
            channelHandlerContext.fireChannelRead(fullHttpRequest.retain());
        }


        // GET Requests
        if (fullHttpRequest.method().equals(HttpMethod.GET)) {

            if (uri.contains("/auth/login?username=")){
                // Get user data
                // Sends challenge to complete in 5 minutes after sending from server
                QueryStringDecoder queryStringDecoder = new QueryStringDecoder(uri);
                Map<String, List<String>> params = queryStringDecoder.parameters();

                // AuthService creates challenge
                MessagePacket challengeResponse = authService.createChallenge(String.valueOf(params.get("username").getFirst()));

                // Send answer
                configureResponseAndSend(channelHandlerContext, challengeResponse);
            }
            else if (uri.contains("/media")){
                mediaHandler.getMedia(channelHandlerContext, fullHttpRequest);
            }

        }

        // POST Requests
        else if (fullHttpRequest.method().equals(HttpMethod.POST) ) {
            String body = fullHttpRequest.content()
                    .toString(CharsetUtil.UTF_8);

            // Getting json from request
            if (uri.startsWith("/auth/")) {
                Map<String, Object> map = JsonConfig.MAPPER.readValue(body, Map.class);
                Map<String, Object> payload = (Map<String, Object>) map.get("payload");
                switch (uri) {
                    // AuthService works like handler and service because of its easy work

                    // If it's registration we register user with AuthService.register()
                    case ("/auth/register") -> {
                        MessagePacket registerResponse = authService.register(String.valueOf(payload.get("username")),
                                String.valueOf(payload.get("ed25519PublicKey")), String.valueOf(payload.get("x25519PublicKey")));
                        // Send answer
                        configureResponseAndSend(channelHandlerContext, registerResponse);
                    }
                    // And verifying user with checking for challenge competion
                    case ("/auth/verify") -> {
                        MessagePacket verifyResponse = authService.login(String.valueOf(payload.get("username")),
                                String.valueOf(payload.get("signature")),
                                String.valueOf(payload.get("challenge")));
                        // Send answer
                        configureResponseAndSend(channelHandlerContext, verifyResponse);
                    }
                }
            }

            else if (uri.startsWith("/media")) {
                mediaHandler.uploadMedia(channelHandlerContext, fullHttpRequest);
            }
        } else if (fullHttpRequest.method().equals(HttpMethod.DELETE)) {
            if (uri.contains("/media")){
                mediaHandler.deleteMedia(channelHandlerContext, fullHttpRequest);
            }
        }

    }


    // Configure HttpResponseStatus using "success" from messagePacket if it does not provided
    private void configureResponseAndSend(ChannelHandlerContext ctx, MessagePacket messagePacket) throws JsonProcessingException {
        HttpResponseStatus httpResponseStatus = messagePacket.payload.get("success").toString().equals("true")
                ? OK
                : BAD_REQUEST;
        sendHttp(ctx, httpResponseStatus, messagePacket);
    }
}
