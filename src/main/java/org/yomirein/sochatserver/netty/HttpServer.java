package org.yomirein.sochatserver.netty;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yomirein.sochatserver.auth.AuthHandler;
import org.yomirein.sochatserver.auth.AuthService;
import org.yomirein.sochatserver.calls.CallHandler;
import org.yomirein.sochatserver.calls.CallService;
import org.yomirein.sochatserver.chats.ChatHandler;
import org.yomirein.sochatserver.friendship.FriendsHandler;
import org.yomirein.sochatserver.media.MediaHandler;
import org.yomirein.sochatserver.messages.MessageHandler;
import org.yomirein.sochatserver.netty.codec.PacketDecoder;
import org.yomirein.sochatserver.netty.codec.PacketEncoder;
import org.yomirein.sochatserver.netty.handlers.HeartbeatHandler;
import org.yomirein.sochatserver.netty.handlers.HttpPacketHandler;
import org.yomirein.sochatserver.netty.handlers.WsPacketHandler;
import org.yomirein.sochatserver.search.SearchHandler;
import org.yomirein.sochatserver.sessions.SessionManager;
import org.yomirein.sochatserver.users.UsersHandler;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.cors.CorsConfig;
import io.netty.handler.codec.http.cors.CorsConfigBuilder;
import io.netty.handler.codec.http.cors.CorsHandler;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class HttpServer {

    // Imports from SoChat.java
    private final int port;

    private final AuthService authService;
    private final CallService callService;

    private final SessionManager sessionManager;

    private final AuthHandler authHandler;
    private final FriendsHandler friendsHandler;
    private final UsersHandler usersHandler;
    private final ChatHandler chatHandler;
    private final MessageHandler messageHandler;
    private final MediaHandler mediaHandler;
    private final CallHandler callHandler;
    private final SearchHandler searchHandler;

    // Adding logger
    private final Logger logger = LoggerFactory.getLogger(HttpServer.class);

    public void run() throws Exception {
        logger.info("Starting Http and WebSocket Server");

        // EventLoopGroups
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        CorsConfig corsConfig = CorsConfigBuilder.forAnyOrigin() //
                // Allows all origins
                .allowedRequestMethods(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE, HttpMethod.OPTIONS)
                .allowCredentials() // To support cookies/credentials in the future
                .allowedRequestHeaders("X-Requested-With", "Content-Type", "Content-Length") // Allowed client headers
                .exposeHeaders("Content-Disposition") // Headers exposed to the client browser
                .build();

        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .handler(new LoggingHandler(LogLevel.TRACE))
                    .childHandler(new ChannelInitializer() {
                        @Override
                        protected void initChannel(Channel channel) throws Exception {
                            ChannelPipeline p = channel.pipeline();

                            // HTTP Server
                            p.addLast(new HttpServerCodec());
                            p.addLast(new HttpObjectAggregator(655369999));

                            // WebSocket server protocol init
                            p.addLast(new WebSocketServerProtocolHandler("/ws", null, true));

                            // Heartbeat for low-level ping pongs
                            p.addLast(new IdleStateHandler(0, 20, 0));
                            p.addLast(new HeartbeatHandler(sessionManager));

                            // Cors
                            p.addLast(new CorsHandler(corsConfig));
                            // HttpPacketHandler init
                            p.addLast(new ChunkedWriteHandler());
                            p.addLast(new HttpPacketHandler(authService, mediaHandler));

                            // WsPacketHandler init, with decoders and encoders
                            p.addLast(new PacketDecoder());
                            p.addLast(new WsPacketHandler(sessionManager, authHandler,
                                    friendsHandler, usersHandler, chatHandler, messageHandler, callHandler, searchHandler, callService));
                            p.addLast(new PacketEncoder());
                        }
                    });

            // Starting server
            Channel channel = b.bind(port).sync().channel();
            channel.closeFuture().sync();
        }
        finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}
