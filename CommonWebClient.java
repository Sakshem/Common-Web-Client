@Slf4j
@RequiredArgsConstructor
@Component
public class CommonWebClient {

    private final WebClient webClient;

    public <T> ResponseEntity<T> postRequest(String baseUrl, String endPoint, HttpHeaders headers, Object requestPayload, Class<T> responseType) {
        long startTime = System.currentTimeMillis();
        log.info(LogConstant.WEBCLIENT_REQUEST, CommonsUtil.logObject(requestPayload));
        T response;
        try {
            var threadContextDetails = fetchDetailsFromThread();
            response = webClient.post()
                    .uri(baseUrl.concat(endPoint))
                    .headers(h -> h.addAll(headers))
                    .bodyValue(requestPayload).retrieve()
                    .bodyToMono(responseType)
                    .contextWrite(Context.of(
                            KeyConstant.SESSION_KEY, StringUtils.isNotBlank(threadContextDetails[0]) ? threadContextDetails[0] : Constants.UNKNOWN,
                            KeyConstant.TRACE_KEY, StringUtils.isNotBlank(threadContextDetails[1]) ? threadContextDetails[1] : Constants.UNKNOWN,
                            KeyConstant.SPAN_KEY, StringUtils.isNotBlank(threadContextDetails[2]) ? threadContextDetails[2] : Constants.UNKNOWN))
                    .block();
            log.info(LogConstant.WEBCLIENT_RESPONSE, CommonsUtil.logObject(response));
        } catch (WebClientResponseException ex) {
            response = handleWebClientResponseException(ex, responseType, endPoint, startTime);
        }
        long endTime = System.currentTimeMillis();
        log.info(LogConstant.WEBCLIENT_RESPONSE_TIME, endPoint, (endTime - startTime));
        return ResponseEntity.ok(response);
    }

    public <T> ResponseEntity<T> getRequest(String baseUrl, String endPoint, HttpHeaders headers, Class<T> responseType) {
        long startTime = System.currentTimeMillis();
        T response = null;
        try {

            var threadContextDetails = fetchDetailsFromThread();
            response = webClient.get()
                    .uri(baseUrl.concat(endPoint))
                    .headers(h -> h.addAll(headers))
                    .retrieve()
                    .bodyToMono(responseType)
                    .contextWrite(Context.of(
                            KeyConstant.SESSION_KEY, StringUtils.isNotBlank(threadContextDetails[0]) ? threadContextDetails[0] : Constants.UNKNOWN,
                            KeyConstant.TRACE_KEY, StringUtils.isNotBlank(threadContextDetails[1]) ? threadContextDetails[1] : Constants.UNKNOWN,
                            KeyConstant.SPAN_KEY, StringUtils.isNotBlank(threadContextDetails[2]) ? threadContextDetails[2] : Constants.UNKNOWN))
                    .block();
            log.info(LogConstant.WEBCLIENT_RESPONSE, CommonsUtil.logObject(response));
        } catch (WebClientResponseException ex) {
            response = handleWebClientResponseException(ex, responseType, endPoint, startTime);
        }
        long endTime = System.currentTimeMillis();
        log.info(LogConstant.WEBCLIENT_RESPONSE_TIME, endPoint, (endTime - startTime));
        return ResponseEntity.ok(response);
    }

    public <T> ResponseEntity<List<T>> getRequestList(String baseUrl, String endPoint, HttpHeaders headers, Class<T> responseType) {
        long startTime = System.currentTimeMillis();
        List<T> response;
        var threadContextDetails = fetchDetailsFromThread();
        try {
            response = webClient.get()
                    .uri(baseUrl.concat(endPoint))
                    .headers(h -> h.addAll(headers))
                    .retrieve()
                    .bodyToFlux(responseType)
                    .collectList()
                    .contextWrite(Context.of(
                            KeyConstant.SESSION_KEY, StringUtils.isNotBlank(threadContextDetails[0]) ? threadContextDetails[0] : Constants.UNKNOWN,
                            KeyConstant.TRACE_KEY, StringUtils.isNotBlank(threadContextDetails[1]) ? threadContextDetails[1] : Constants.UNKNOWN,
                            KeyConstant.SPAN_KEY, StringUtils.isNotBlank(threadContextDetails[2]) ? threadContextDetails[2] : Constants.UNKNOWN))
                    .block();
            log.info(LogConstant.WEBCLIENT_RESPONSE, CommonsUtil.logObject(response));
        } catch (WebClientResponseException ex) {
            String responseBody = extractAndLogException(ex);
            if (StringUtils.isNotBlank(responseBody)) {
                response = ex.getResponseBodyAs(new ParameterizedTypeReference<List<T>>() {
                });
            } else {
                long endTime = System.currentTimeMillis();
                log.info(LogConstant.WEBCLIENT_RESPONSE_TIME, endPoint, (endTime - startTime));
                throw ex;
            }
        }
        long endTime = System.currentTimeMillis();
        log.info(LogConstant.WEBCLIENT_RESPONSE_TIME, endPoint, (endTime - startTime));
        return ResponseEntity.ok(response);
    }

    public <T> Mono<T> postRequestMono(String baseUrl, String endPoint, HttpHeaders headers, Object requestPayload, Class<T> responseType) {
        return webClient.post()
                .uri(baseUrl.concat(endPoint))
                .headers(h -> h.addAll(headers))
                .bodyValue(requestPayload)
                .retrieve()
                .bodyToMono(responseType);
    }

    private <T> T handleWebClientResponseException(WebClientResponseException ex, Class<T> responseType, String endPoint, long startTime) {
        String responseBody = extractAndLogException(ex);
        if (StringUtils.isNotBlank(responseBody)) {
            return ex.getResponseBodyAs(responseType);
        } else {
            long endTime = System.currentTimeMillis();
            log.info(LogConstant.WEBCLIENT_RESPONSE_TIME, endPoint, (endTime - startTime));
            throw ex;
        }
    }

    private String extractAndLogException(WebClientResponseException ex) {
        HttpStatusCode statusCode = ex.getStatusCode();
        String message = ex.getMessage();
        String responseBody = ex.getResponseBodyAsString();
        Throwable cause = ex.getCause();
        log.info(LogConstant.WEBCLIENT_EXCEPTION, statusCode, message, ex.getMessage(), responseBody, cause);
        return responseBody;
    }

    private String[] fetchDetailsFromThread() {
        var threadContextDetails = new String[3];
        threadContextDetails[0] = ThreadContext.get(KeyConstant.SESSION_KEY);
        threadContextDetails[1] = ThreadContext.get(KeyConstant.TRACE_KEY);
        threadContextDetails[2] = ThreadContext.get(KeyConstant.SPAN_KEY);
        return threadContextDetails;
    }
}
