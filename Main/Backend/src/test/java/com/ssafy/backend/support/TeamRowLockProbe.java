package com.ssafy.backend.support;

import com.ssafy.backend.space.repository.TeamRepository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * "이 서비스 호출이 teams 행 잠금을 기다리는가"를 결정적으로 확인하는 테스트 도구.
 *
 * <p>경합을 여러 번 돌려 우연히 겹치기를 기대하는 대신, 테스트가 별도 트랜잭션에서 teams 행을
 * {@code FOR UPDATE}로 먼저 잡고 그동안 서비스 호출이 끝나는지 본다. 잠금 조회를 쓰는 경로는
 * 잠금이 풀릴 때까지 끝나지 못하고, 쓰지 않는 경로는 바로 끝난다.</p>
 */
public class TeamRowLockProbe {

    /** 잠금을 쥔 동안 호출이 끝나는지 지켜보는 시간. 잠금 없는 호출은 수십 ms 안에 끝난다. */
    private static final long OBSERVE_MILLIS = 700;
    private static final long FINISH_SECONDS = 10;

    private final TransactionTemplate transactionTemplate;
    private final TeamRepository teamRepository;

    public TeamRowLockProbe(PlatformTransactionManager transactionManager, TeamRepository teamRepository) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.teamRepository = teamRepository;
    }

    /** 호출 결과. 잠금이 풀린 뒤의 반환값 또는 예외를 담는다. */
    public record Outcome<T>(boolean waitedForLock, T value, Throwable error) {
    }

    /**
     * teams 행을 잠근 상태에서 {@code call}을 다른 스레드로 실행한다.
     *
     * @return 잠금을 쥔 동안 호출이 끝나지 않았으면 {@code waitedForLock = true}
     */
    public <T> Outcome<T> callWhileTeamLocked(Long teamId, Callable<T> call) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            Future<?> holder = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                teamRepository.findActiveByIdForUpdate(teamId).orElseThrow();
                locked.countDown();
                try {
                    release.await(FINISH_SECONDS, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
            if (!locked.await(FINISH_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("테스트가 teams 행 잠금을 잡지 못했습니다.");
            }

            Future<T> pending = executor.submit(call);
            boolean waited = false;
            try {
                pending.get(OBSERVE_MILLIS, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                waited = true;
            } catch (ExecutionException e) {
                // 잠금을 기다리지 않고 예외로 끝난 경우 — 아래에서 다시 꺼낸다.
            }

            release.countDown();
            holder.get(FINISH_SECONDS, TimeUnit.SECONDS);
            try {
                return new Outcome<>(waited, pending.get(FINISH_SECONDS, TimeUnit.SECONDS), null);
            } catch (ExecutionException e) {
                return new Outcome<>(waited, null, e.getCause());
            }
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }
}
