@Slf4j
@RequiredArgsConstructor
@Configuration
public class WebClientConfig {
    private final PropertyConfig propertyConfig; // class to read properties from env

    private static final String CONNECTION_TIME = "webclient.connection.timeout";
    private static final String READ_TIME = "webclient.read.timeout";
    private static final String MAX_CONNECTIONS = "webclient.maxConnection";
    private static final String PENDING_ACQUIRE_TIMEOUT = "webclient.pendingAcquireTimeout";
    private static final String IDLE_TIME = "webclient.idle.timeout";
    private static final String EVICTION_INTERVAL = "webclient.eviction.interval";
    private static final int DEFAULT_CONNECTION_TIME = 5000;
    private static final int DEFAULT_REQUEST_TIME = 15000;

    @Bean
    public WebClient webClient(WebClient.Builder builder) throws SSLException {
        int connectionTimeOut = propertyConfig.getInteger(CONNECTION_TIME, DEFAULT_CONNECTION_TIME);
        int readTimeOut = propertyConfig.getInteger(READ_TIME, DEFAULT_REQUEST_TIME);

        SslContext sslContext = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build();

        ConnectionProvider provider = ConnectionProvider.builder("MY-SERVICE")
                .maxConnections(propertyConfig.getInteger(MAX_CONNECTIONS, 100))
                .pendingAcquireTimeout(Duration.ofMillis(propertyConfig.getInteger(PENDING_ACQUIRE_TIMEOUT, 16000)))
                .maxIdleTime(Duration.ofMillis(propertyConfig.getInteger(IDLE_TIME, 150000))) // Default 150 seconds
                .evictInBackground(Duration.ofMillis(propertyConfig.getInteger(EVICTION_INTERVAL, 30000))) // Default 30 seconds
                .build();
        HttpClient httpClient = HttpClient.create(provider)
                .responseTimeout(Duration.ofMillis(readTimeOut))  // Response timeout
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectionTimeOut).secure(t -> t.sslContext(sslContext));  // Connection timeout


        return builder.clientConnector
                (new ReactorClientHttpConnector(httpClient)).filter(logRequest()).filter(logResponse()).build();
    }

    private static ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            log.info("Request: {} {}", clientRequest.method(), clientRequest.url());
            clientRequest.headers().forEach((name, values) -> values.forEach(value -> log.info("{}={}", name, value)));
            return Mono.just(clientRequest);
        });
    }

    private static ExchangeFilterFunction logResponse() {
        return ExchangeFilterFunction.ofResponseProcessor(clientResponse ->
                Mono.deferContextual(contextView -> {
                    // Extract the trace ID from the Reactor Context

                    String sessionId = contextView.getOrDefault(KeyConstant.SESSION_KEY, "");
                    String traceId = contextView.getOrDefault(KeyConstant.TRACE_KEY, "");
                    String spanId = contextView.getOrDefault(KeyConstant.SPAN_KEY, "");

                    ThreadContext.put(KeyConstant.SESSION_KEY, sessionId);
                    ThreadContext.put(KeyConstant.TRACE_KEY, traceId);
                    ThreadContext.put(KeyConstant.SPAN_KEY, spanId);
                    // Log the response with the trace ID
                    try {
                        log.info("Response status: {}", clientResponse.statusCode());
                        clientResponse.headers().asHttpHeaders().forEach((name, values) -> values.forEach(value -> log.info("{}={}", name, value)));

                        // Return the original response
                        return Mono.just(clientResponse);
                    } finally {
                        ThreadContext.remove(KeyConstant.SESSION_KEY);
                        ThreadContext.remove(KeyConstant.TRACE_KEY);
                        ThreadContext.remove(KeyConstant.SPAN_KEY);
                    }
                })
        );
    } 
}
