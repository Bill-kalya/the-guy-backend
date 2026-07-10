package com.theguy.app.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthInterceptor webSocketAuthInterceptor;

    @Value("${WEBSOCKET_ALLOWED_ORIGIN_1:https://app.theguy.co.ke}")
    private String websocketAllowedOrigin1;

    @Value("${WEBSOCKET_ALLOWED_ORIGIN_2:http://localhost:3000}")
    private String websocketAllowedOrigin2;

    @Value("${WEBSOCKET_SOCKJS_CLIENT_URL:https://cdn.jsdelivr.net/npm/sockjs-client@1/dist/sockjs.min.js}")
    private String sockJsClientLibraryUrl;

    private String[] websocketAllowedOrigins() {
        return new String[]{websocketAllowedOrigin1, websocketAllowedOrigin2};
    }


    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // NOTE: SockJS client-library URL + allowed origins are configured via application.yml
        // to avoid hardcoding environments.
        registry.addEndpoint("/ws")
                .setAllowedOrigins(websocketAllowedOrigins())

                .withSockJS()
                .setClientLibraryUrl(sockJsClientLibraryUrl)
                .setStreamBytesLimit(512 * 1024)
                .setHttpMessageCacheSize(1000)
                .setDisconnectDelay(30 * 1000);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(128 * 1024)
                   .setSendBufferSizeLimit(512 * 1024)
                   .setSendTimeLimit(20000);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(webSocketAuthInterceptor);
        registration.taskExecutor()
                    .corePoolSize(4)
                    .maxPoolSize(10)
                    .keepAliveSeconds(60);
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.taskExecutor()
                    .corePoolSize(4)
                    .maxPoolSize(10)
                    .keepAliveSeconds(60);
    }
}
