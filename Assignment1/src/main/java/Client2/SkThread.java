package Client2;

import config.AppConfig;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.ApiResponse;
import io.swagger.client.api.SkiersApi;
import io.swagger.client.model.LiftRide;
import com.google.common.util.concurrent.RateLimiter;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SkThread is responsible for sending multiple POST requests to the skier API,
 * simulating skier lift rides. It handles retries for each request and records
 * response times and status codes for throughput analysis.
 */
public class SkThread implements Runnable {
    private static final int RETRY_TIMES = 5;
    private static final Logger LOGGER = Logger.getLogger(SkThread.class.getName());
    private static final RateLimiter DEFAULT_RATE_LIMITER = RateLimiter.create(AppConfig.getClient2RateLimit());
    private static final int FAILURE_THRESHOLD = AppConfig.getClient2FailureThreshold();
    private static final long CIRCUIT_BREAKER_TIMEOUT = AppConfig.getClient2CircuitBreakerTimeoutMs();
    private final BlockingQueue<LiftRide> rideQueue;
    private final CountDownLatch curLatch;
    private final ApiClient apiClient;
    private final int numRequests;
    private final RateLimiter rateLimiter;

    // 断路器相关变量
    private static volatile boolean circuitBreakerOpen = false;
    private static final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private static long circuitBreakerOpenedTime = 0;

    public SkThread(BlockingQueue<LiftRide> rideQueue, CountDownLatch curLatch, ApiClient apiClient, int numRequests, RateLimiter rateLimiter) {
        this.rideQueue = rideQueue;
        this.curLatch = curLatch;
        this.apiClient = apiClient;
        this.numRequests = numRequests;
        this.rateLimiter = rateLimiter != null ? rateLimiter : DEFAULT_RATE_LIMITER;
    }

    @Override
    public void run() {
        SkiersApi apiInstance = new SkiersApi(apiClient);

        for (int i = 0; i < numRequests; i++) {
            try {
                // 等待获取许可，限流控制
                rateLimiter.acquire();

                // 检查断路器状态
                if (circuitBreakerOpen) {
                    // 检查是否可以尝试恢复
                    if (System.currentTimeMillis() - circuitBreakerOpenedTime > CIRCUIT_BREAKER_TIMEOUT) {
                        circuitBreakerOpen = false;
                        consecutiveFailures.set(0);
                        LOGGER.info("Circuit breaker closed. Resuming requests.");
                    } else {
                        // 暂停一段时间再检查
                        Thread.sleep(1000);
                        i--; // 不计入请求次数
                        continue;
                    }
                }

                // 从共享队列中获取一个 LiftRide 事件
                LiftRide ride = rideQueue.poll(1, TimeUnit.SECONDS);
                if (ride == null) {
                    break;
                }
                SkEvent skierEvent = new SkEvent();

                // 尝试多次请求，最多重试 RETRY_TIMES 次
                boolean success = false;
                for (int j = 0; j < RETRY_TIMES; j++) {
                    try {
                        long startTime = System.currentTimeMillis();
                        // 发送 POST 请求
                        ApiResponse<Void> res = apiInstance.writeNewLiftRideWithHttpInfo(
                                ride, skierEvent.getResortID(), skierEvent.getSeasonID(), skierEvent.getDayID(), skierEvent.getSkierID());
                        long endTime = System.currentTimeMillis();

                        // 记录成功请求的详细信息
                        RecordProcessor.records.add(new Record(startTime, "POST", endTime - startTime, res.getStatusCode()));
                        SkClient2.counter.incrementSuccessfulPost(1);
                        consecutiveFailures.set(0); // 重置失败计数
                        success = true;
                        break; // 请求成功后退出重试循环
                    } catch (ApiException e) {
                        if (e.getCode() >= 400 && e.getCode() < 600) {
                            // 记录失败请求，并根据状态码决定是否重试
                            SkClient2.counter.incrementFailedPost(1);
                            LOGGER.warning("请求失败，HTTP 状态码: " + e.getCode() + "，重试次数: " + (j + 1));
                        }
                        // 在重试次数达到上限时打印错误并停止重试
                        if (j == RETRY_TIMES - 1) {
                            LOGGER.severe("请求多次失败，放弃请求");
                            e.printStackTrace();
                        }
                        // 引入指数退避
                        int backoffTime = (int) Math.pow(2, j);
                        Thread.sleep(Math.min(backoffTime * 1000, 10000)); // 最大等待时间 10 秒
                    }
                }

                if (!success) {
                    // 请求失败，增加失败计数
                    int failures = consecutiveFailures.incrementAndGet();
                    if (failures >= FAILURE_THRESHOLD) {
                        // 触发断路器
                        circuitBreakerOpen = true;
                        circuitBreakerOpenedTime = System.currentTimeMillis();
                        LOGGER.warning("Circuit breaker opened due to consecutive failures.");
                    }
                }

            } catch (InterruptedException e) {
                // 捕获线程中断异常
                LOGGER.log(Level.SEVERE, "线程被中断", e);
            }
        }

        // 当前线程处理完成，减少 CountDownLatch 的计数
        curLatch.countDown();
    }
}
