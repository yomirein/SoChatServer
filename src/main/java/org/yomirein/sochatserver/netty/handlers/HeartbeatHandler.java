package org.yomirein.sochatserver.netty.handlers;

import org.yomirein.sochatserver.sessions.SessionManager;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.RequiredArgsConstructor;


@RequiredArgsConstructor
public class HeartbeatHandler extends ChannelInboundHandlerAdapter {

    private final SessionManager sessionManager;

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent e) {

            if (e.state() == IdleState.WRITER_IDLE) {
                ctx.writeAndFlush(new PingWebSocketFrame());
            }
            else if (e.state() == IdleState.READER_IDLE) {
                if (sessionManager.isAuthenticated(ctx.channel())) {
                    sessionManager.removeSession(ctx.channel());
                }
                ctx.close();
            }
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof PongWebSocketFrame) {
            return;
        }
        super.channelRead(ctx, msg);
    }
}