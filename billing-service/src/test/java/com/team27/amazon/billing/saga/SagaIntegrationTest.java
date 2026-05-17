package com.team27.amazon.billing.saga;

import com.team27.amazon.billing.model.Transaction;
import com.team27.amazon.billing.model.TransactionStatus;
import com.team27.amazon.billing.repository.TransactionRepository;
import com.team27.amazon.contracts.events.OrderCancelledEvent;
import com.team27.amazon.contracts.events.OrderCompletedEvent;
import com.team27.amazon.contracts.events.OrderItemPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration")
class SagaIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient
                .bindToServer()
                .baseUrl("http://localhost:" + port)
                .defaultHeader("Authorization", "Bearer test-jwt-token-user-1")
                .defaultHeader("Content-Type", "application/json")
                .build();

        // Clean up any existing transactions for orderId=10
        transactionRepository.findAll().stream()
                .filter(t -> t.getOrderId() != null && t.getOrderId().equals(10L))
                .forEach(transactionRepository::delete);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SCENARIO A — Happy path end-to-end
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Scenario A — order.completed consumed → PENDING transaction created")
    void scenarioA_orderCompletedConsumer_createsPendingTransaction() {
        OrderCompletedEvent event = new OrderCompletedEvent(
                10L, 1L, 1L, 45350.0
        );
        rabbitTemplate.convertAndSend("order.events", "order.completed", event);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Optional<Transaction> txn =
                    transactionRepository.findPendingTransactionByOrderId(10L);
            assertThat(txn).isPresent();
            assertThat(txn.get().getStatus()).isEqualTo(TransactionStatus.PENDING);
            assertThat(txn.get().getAmount()).isEqualTo(45350.0);
            assertThat(txn.get().getUserId()).isEqualTo(1L);
            assertThat(txn.get().getOrderId()).isEqualTo(10L);
        });
    }

    @Test
    @DisplayName("Scenario A — POST /api/transactions/order/10 → 201, status=COMPLETED")
    void scenarioA_processPayment_returnsCompletedTransaction() {
        // Seed PENDING transaction
        Transaction pending = new Transaction();
        pending.setOrderId(10L);
        pending.setUserId(1L);
        pending.setAmount(45350.0);
        pending.setStatus(TransactionStatus.PENDING);
        pending.setCreatedAt(LocalDateTime.now());
        transactionRepository.save(pending);

        webTestClient.post()
                .uri("/api/transactions/order/10")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("method", "CREDIT_CARD", "cardLastFour", "4242"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Transaction.class)
                .value(body -> {
                    assertThat(body.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
                    assertThat(body.getOrderId()).isEqualTo(10L);
                    assertThat(body.getAmount()).isEqualTo(45350.0);
                });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SCENARIO B — Payment failure and compensation
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Scenario B — simulateFailure=true → 200, status=FAILED")
    void scenarioB_simulateFailure_returnsFailedTransaction() {
        // Seed PENDING transaction
        Transaction pending = new Transaction();
        pending.setOrderId(10L);
        pending.setUserId(1L);
        pending.setAmount(45350.0);
        pending.setStatus(TransactionStatus.PENDING);
        pending.setCreatedAt(LocalDateTime.now());
        transactionRepository.save(pending);

        webTestClient.post()
                .uri("/api/transactions/order/10?simulateFailure=true")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("method", "CREDIT_CARD", "cardLastFour", "4242"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(Transaction.class)
                .value(body -> {
                    assertThat(body.getStatus()).isEqualTo(TransactionStatus.FAILED);
                });
    }

    @Test
    @DisplayName("Scenario B — order.cancelled + COMPLETED tx → REFUNDED")
    void scenarioB_orderCancelledConsumer_refundsCompletedTransaction() {
        // Seed COMPLETED transaction
        Transaction completed = new Transaction();
        completed.setOrderId(10L);
        completed.setUserId(1L);
        completed.setAmount(45350.0);
        completed.setStatus(TransactionStatus.COMPLETED);
        completed.setCreatedAt(LocalDateTime.now());
        transactionRepository.save(completed);

        OrderCancelledEvent event = new OrderCancelledEvent(
                10L, 1L,
                List.of(
                        new OrderItemPayload(1L, 1, 25000.0),
                        new OrderItemPayload(3L, 1, 20350.0)
                ),
                "payment_failed"
        );
        rabbitTemplate.convertAndSend("order.events", "order.cancelled", event);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Transaction txn = transactionRepository.findById(completed.getId())
                    .orElseThrow();
            assertThat(txn.getStatus()).isEqualTo(TransactionStatus.REFUNDED);
        });
    }

    @Test
    @DisplayName("Scenario B — order.cancelled + PENDING tx → FAILED")
    void scenarioB_orderCancelledConsumer_failsPendingTransaction() {
        // Seed PENDING transaction
        Transaction pending = new Transaction();
        pending.setOrderId(10L);
        pending.setUserId(1L);
        pending.setAmount(45350.0);
        pending.setStatus(TransactionStatus.PENDING);
        pending.setCreatedAt(LocalDateTime.now());
        transactionRepository.save(pending);

        OrderCancelledEvent event = new OrderCancelledEvent(
                10L, 1L,
                List.of(
                        new OrderItemPayload(1L, 1, 25000.0),
                        new OrderItemPayload(3L, 1, 20350.0)
                ),
                "payment_failed"
        );
        rabbitTemplate.convertAndSend("order.events", "order.cancelled", event);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Transaction txn = transactionRepository.findById(pending.getId())
                    .orElseThrow();
            assertThat(txn.getStatus()).isEqualTo(TransactionStatus.FAILED);
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SCENARIO C — Pre-check failure (no active shipment)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Scenario C — no order.completed published → no transaction created")
    void scenarioC_noOrderCompletedEvent_noTransactionCreated() throws InterruptedException {
        // Do NOT publish any event — S3 aborted before publishing
        TimeUnit.SECONDS.sleep(5);

        Optional<Transaction> txn =
                transactionRepository.findPendingTransactionByOrderId(10L);
        assertThat(txn).isEmpty();

        int completedCount =
                transactionRepository.countCompletedTransactionsByOrderId(10L);
        assertThat(completedCount).isZero();
    }

    @Test
    @DisplayName("Scenario C — POST payment with no PENDING transaction → 404")
    void scenarioC_processPaymentWithNoSagaState_returns404() {
        webTestClient.post()
                .uri("/api/transactions/order/10")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("method", "CREDIT_CARD", "cardLastFour", "4242"))
                .exchange()
                .expectStatus().isNotFound()
                .expectBody(String.class)
                .value(body ->
                        assertThat(body).contains("Transaction not initialized for this order")
                );
    }

    @Test
    @DisplayName("Scenario C — duplicate POST → first 201, second 409 Conflict")
    @SuppressWarnings("unchecked")
    void scenarioC_duplicatePaymentRequest_secondReturns409() throws InterruptedException {
        // Seed ONE PENDING transaction
        Transaction pending = new Transaction();
        pending.setOrderId(10L);
        pending.setUserId(1L);
        pending.setAmount(45350.0);
        pending.setStatus(TransactionStatus.PENDING);
        pending.setCreatedAt(LocalDateTime.now());
        transactionRepository.save(pending);

        HttpStatus[] statuses = new HttpStatus[2];

        Thread t1 = new Thread(() -> {
            WebTestClient.ResponseSpec r = webTestClient.post()
                    .uri("/api/transactions/order/10")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("method", "CREDIT_CARD", "cardLastFour", "4242"))
                    .exchange();
            statuses[0] = (HttpStatus) r.returnResult(String.class)
                    .getStatus();
        });

        Thread t2 = new Thread(() -> {
            WebTestClient.ResponseSpec r = webTestClient.post()
                    .uri("/api/transactions/order/10")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("method", "CREDIT_CARD", "cardLastFour", "4242"))
                    .exchange();
            statuses[1] = (HttpStatus) r.returnResult(String.class)
                    .getStatus();
        });

        t1.start();
        t2.start();
        t1.join();
        t2.join();

        boolean oneCreated = statuses[0] == HttpStatus.CREATED
                || statuses[1] == HttpStatus.CREATED;
        boolean oneConflict = statuses[0] == HttpStatus.CONFLICT
                || statuses[1] == HttpStatus.CONFLICT;

        assertThat(oneCreated).isTrue();
        assertThat(oneConflict).isTrue();
    }
}