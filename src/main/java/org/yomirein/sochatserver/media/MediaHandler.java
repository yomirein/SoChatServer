package org.yomirein.sochatserver.media;


import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URLConnection;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yomirein.sochatserver.chats.Chat;
import org.yomirein.sochatserver.chats.ChatService;
import org.yomirein.sochatserver.utils.MessageSender;
import static org.yomirein.sochatserver.utils.MessageSender.sendHttp;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpChunkedInput;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponse;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.netty.handler.stream.ChunkedFile;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class MediaHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(MediaHandler.class);

    private final MediaService mediaService;
    private final ChatService chatService;

    public void getMedia(ChannelHandlerContext ctx, FullHttpRequest fullHttpRequest) {
        if (!fullHttpRequest.decoderResult().isSuccess()) {
            MessageSender.sendHttp(ctx, BAD_REQUEST, "Bad request");
            return;
        }

        try {
            Media media = mediaService.getMediaFile(fullHttpRequest.uri());

            RandomAccessFile raf = new RandomAccessFile(media.getFile(), "r");
            HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);

            String contentType = URLConnection.guessContentTypeFromName(media.getFile().getName());
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType != null ? contentType : "application/octet-stream");
            response.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);

            response.headers().set(
                    HttpHeaderNames.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + media.getFileName() + "\""
            );
            // TODO: Make that only chat member can get chat media after moving everything in TCP protocol
            ctx.write(response);
            ctx.writeAndFlush(new HttpChunkedInput(new ChunkedFile(raf)))
                    .addListener(ChannelFutureListener.CLOSE);

        } catch (MediaException e) {
            sendHttp(ctx, e.getStatus(), e.getMessage());
        } catch (IOException e) {
            sendHttp(ctx, INTERNAL_SERVER_ERROR, "IO Error");
            throw new RuntimeException(e);
        }
    }

    public void uploadMedia(ChannelHandlerContext ctx, FullHttpRequest request) {
        DefaultHttpDataFactory factory = new DefaultHttpDataFactory(true);
        HttpPostRequestDecoder decoder = new HttpPostRequestDecoder(factory, request);

        try {
            String token = null;

            String authHeader = request.headers().get(HttpHeaderNames.AUTHORIZATION);
            String nonce = request.headers().get("x-nonce");

            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7).trim();
            }

            FileUpload file = null;

            while (decoder.hasNext()) {
                InterfaceHttpData data = decoder.next();

                if (data.getHttpDataType() != InterfaceHttpData.HttpDataType.Attribute 
                && data.getHttpDataType() == InterfaceHttpData.HttpDataType.FileUpload) {
                    file = (FileUpload) data;
                }
            }
            if (file != null) {
                try {
                    String mediaId = mediaService.saveUploadedFile(token, file, nonce);
                    sendHttp(ctx, OK, mediaId);
                } catch (MediaException e) {
                    sendHttp(ctx, e.getStatus(), e.getMessage());
                } catch (IOException e) {
                    sendHttp(ctx, INTERNAL_SERVER_ERROR, "Contact administrator about this error");
                    throw new RuntimeException(e);
                }
            }
        } catch (RuntimeException e) {
            LOGGER.error("Error uploading a file", e);
            sendHttp(ctx, INTERNAL_SERVER_ERROR, "Upload failed");
        } finally {
            decoder.destroy();
        }
    }

    public void deleteMedia(ChannelHandlerContext ctx, FullHttpRequest fullHttpRequest) {
        try {
            QueryStringDecoder decoder = new QueryStringDecoder(fullHttpRequest.uri());
            Map<String, List<String>> parameters = decoder.parameters();

            String mediaId = parameters.get("id").getFirst();

            Media mediaFile = mediaService.getMediaFile(mediaId);
            Chat chat = chatService.getChatByMessageId(mediaFile.getMessageId(), true);

            // TODO: Make that only media sender can delete their message after moving everything in TCP protocol
            if (chat.getParticipants().stream().anyMatch((p) -> p.getUserId() == mediaFile.getSenderId())){
                mediaService.deleteMedia(mediaId);
                sendHttp(ctx, OK, "Deleted successfully");
            } else {
                sendHttp(ctx, FORBIDDEN, "Only chat members can delete their media");
            }



        } catch (MediaException e) {
            sendHttp(ctx, e.getStatus(), e.getMessage());
        }
    }
}
