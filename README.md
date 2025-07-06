# Common-Web-Client
One WebClient to Rule Them All: Building a Common Web Client for Microservice Calls
### 🚀 One WebClient to Rule Them All: Building a **Common Web Client** for Microservice Calls

*(and never writing the same boiler‑plate again)*

---

### 1. Why you should care

Picture this: you join a code‑base with **18 different “FooBarWebClient” classes**—each one hard‑coding the same timeouts, headers, and logging but pointing at a different microservice.

*Every* new service means another copy‑paste, another place for bugs, and another PR to tweak a timeout.

A **Common Web Client** flips that script. You configure once, reuse everywhere, and keep your mental RAM free for business logic instead of plumbing.

---

### 2. First, the buzzwords (plain‑English edition)

| Term | Human‑sized meaning | Why it matters |
| --- | --- | --- |
| **Connection timeout** | How long we’ll wait while *dialling* the server before giving up. | Bad Wi‑Fi? Slow VPN? Fail fast instead of hanging threads. |
| **Read timeout** | How long we’ll wait *after* a connection is open but before the first byte of the response arrives. | Protects from “server went to lunch” scenarios. |
| **Max connections** | Upper limit of concurrent TCP pipes in the pool. | Stops a runaway loop from eating every socket on the box. |
| **Pending‑acquire timeout** | How long a caller may wait to *grab* a free connection from the pool. | Provides back‑pressure: better to timeout than DOS yourself. |
| **Idle time** | How long an unused connection may sit in the pool before eviction. | Frees resources during quiet periods. |
| **Eviction interval** | How often the pool scans for and closes idle zombies. | Keeps the pool healthy without you babysitting it. |

Keep those in your mental glossary—each one shows up in the config you’re about to see.

---

### 3. Meet **WebClient**—the reactive HTTP workhorse

Spring’s `WebClient` is:

- **Non‑blocking** by nature (Flux/Mono under the hood).
- **Fluent**: chain methods like `.get().uri("/orders").retrieve()…`.
- **Extensible**: plug filters for logging, tracing, auth, retry, metrics.

But its super‑power is **sharing**—one bean, many consumers. That’s where our Common Web Client shines.

---

### 4. Designing the Common Web Client

### 4.1 Goals

1. **Single place** to tweak timeouts, SSL, connection pool.
2. **Consistent logging & tracing** injected automatically.
3. Offer both **blocking** (easy migration) *and* **reactive** flavours.
4. **Graceful error handling**—never lose the server’s error body.

### 4.2 The configuration (boilerplate once, profit forever)

```java

@Bean
public WebClient webClient(WebClient.Builder builder) throws SSLException {
    int connect = property.getInteger("webclient.connection.timeout", 5000);
    int read    = property.getInteger("webclient.read.timeout",      15000);

    ConnectionProvider provider = ConnectionProvider.builder("MY-SERVICE")
        .maxConnections(property.getInteger("webclient.maxConnection", 100))
        .pendingAcquireTimeout(Duration.ofMillis(property.getInteger("webclient.pendingAcquireTimeout", 16000)))
        .maxIdleTime(Duration.ofMillis(property.getInteger("webclient.idle.timeout", 150000)))
        .evictInBackground(Duration.ofMillis(property.getInteger("webclient.eviction.interval", 30000)))
        .build();

    HttpClient httpClient = HttpClient.create(provider)
        .responseTimeout(Duration.ofMillis(read))
        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connect)
        .secure(spec -> spec.sslContext(trustAll()));   // demo only

    return builder
        .clientConnector(new ReactorClientHttpConnector(httpClient))
        .filter(logRequest())
        .filter(logResponse())
        .build();
}

```

**Key take‑aways**

- **ConnectionProvider** sets pool limits & hygiene.
- **HttpClient** overlays per‑request timeouts.
- Filters add structured logs **once**—visible on every call.

### 4.3 The façade (typed, trace‑aware helpers)

The `CommonWebClient` class wraps that bean with *friendly* methods:

```java

public <T> ResponseEntity<T> postRequest(...) { …block(); }
public <T> ResponseEntity<T> getRequest(...)  { …block(); }
public <T> Mono<T>          postRequestMono(...) { …non‑blocking… }
```

Inside each method it:

1. **Grabs trace/session IDs** from `ThreadContext` and injects them into Reactor’s `Context`.
2. **Logs** the outgoing payload (`WEBCLIENT_REQUEST`) and, later, the response (`WEBCLIENT_RESPONSE`).
3. **Handles `WebClientResponseException`** centrally—parsing the error body so callers still get useful details.
4. **Measures latency** per endpoint (`WEBCLIENT_RESPONSE_TIME`).

Result: any service can `@Autowired CommonWebClient` and call

```java

commonWebClient.postRequest(baseUrl, "/v1/pay", headers, dto, PayResponse.class);
```

— no need to juggle a dozen bespoke clients.

---

### 5. How exception handling really works

- If the remote service returns **4xx/5xx**, `WebClient` throws `WebClientResponseException`.
- The helper logs status + body, then:
    - **If** the body is present → deserialises it into your DTO and returns it.
    - **Else** (maybe a 500 with empty body) → rethrows, bubbling up the original stack‑trace.
- Either way you never lose the context or the timer metrics.

---

### 6. Top benefits at a glance

| ✅ Win | What it means for you |
| --- | --- |
| **DRY networking layer** | One bean, zero duplication, faster onboarding for new devs. |
| **Consistent observability** | Every request/response auto‑logged with latency and trace IDs—no more “where did that call go?”. |
| **Centralised performance tuning** | Need to raise `maxConnections` for a Black‑Friday spike? Change a single property. |
| **Smooth migration path** | Blocking helpers for legacy code, `Mono` for new reactive flows—no big‑bang rewrite. |
| **Pluggable filters** | Drop‑in auth headers, retry policies, or circuit breakers without touching business code. |
| **Built‑in safety nets** | Sensible default timeouts prevent the dreaded “half‑open” thread leak. |

---

### 7. Wrap‑up & what’s next

With a **Common Web Client** you trade boiler‑plate for focus.

Your team stops writing copy‑paste wrappers and starts shipping features.

Performance tuning becomes a **config change**, not a hunt across 14 repos.

Want to take it further?

- Add **`Retry`** and **`CircuitBreaker`** filters (Resilience4j).
- Export connection‑pool metrics to **Prometheus**.
- Implement **distributed tracing** by propagating the Reactor `Context` into Zipkin/Jaeger spans automatically.

Until then, enjoy the calm of having *one* place to tweak your HTTP layer—and may your threads forever be non‑blocking. Happy coding! 🎉
