package com.petgrooming.pet_system.support;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 讓多個執行緒在同一瞬間執行同一段程式，用來驗證悲觀鎖是否真的把併發請求序列化。
 */
public final class Concurrently {

    private Concurrently() {
    }

    public record Result(int succeeded, List<Throwable> failures) {
        public int failed() {
            return failures.size();
        }
    }

    public static Result run(int threads, ThrowingRunnable task) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                task.run();
                return null;
            }));
        }
        ready.await(10, TimeUnit.SECONDS);
        go.countDown();

        int succeeded = 0;
        List<Throwable> failures = new ArrayList<>();
        for (Future<?> future : futures) {
            try {
                future.get(60, TimeUnit.SECONDS);
                succeeded++;
            } catch (Exception e) {
                failures.add(e.getCause() != null ? e.getCause() : e);
            }
        }
        pool.shutdownNow();
        return new Result(succeeded, failures);
    }

    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }
}
