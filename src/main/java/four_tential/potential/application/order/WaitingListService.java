package four_tential.potential.application.order;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.common.exception.domain.OrderExceptionEnum;
import four_tential.potential.domain.order.WaitingListRepository;
import four_tential.potential.infra.redis.RedisConstants;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class WaitingListService {

    private final WaitingListRepository waitingListRepository;
    private final StringRedisTemplate redisTemplate;
    private final RedissonClient redissonClient;

    public boolean tryOccupyingStock(UUID courseId, UUID memberId) {
        String lockKey = RedisConstants.ORDER_LOCK_PREFIX + courseId + ":" + memberId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // 1. 분산 락 획득 시도 (최대 5초 대기, 락 획득 후 10초 유지)
            if (lock.tryLock(5, 10, TimeUnit.SECONDS)) {
                String occupancyKey = RedisConstants.USER_COURSE_OCCUPANCY_PREFIX + courseId + ":" + memberId;
                String stockKey = RedisConstants.COURSE_STOCK_PREFIX + courseId;

                // 2. 이미 해당 강의의 재고를 점유하고 있는지 확인
                if (Boolean.TRUE.equals(redisTemplate.hasKey(occupancyKey))) {
                    throw new ServiceErrorException(OrderExceptionEnum.ERR_DUPLICATE_ORDER);
                }

                // 3. Redis에서 원자적으로 재고 차감
                Long stock = redisTemplate.opsForValue().decrement(stockKey);

                if (stock != null && stock >= 0) {
                    // 4. 재고 점유 성공 시 사용자별 점유 상태 기록 (10분 유지)
                    redisTemplate.opsForValue().set(occupancyKey, "OCCUPIED", Duration.ofMinutes(10));
                    return true;
                }

                // 5. 재고 부족 시 다시 원복
                redisTemplate.opsForValue().increment(stockKey);
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
        return false;
    }

    public void rollbackOccupiedStock(UUID courseId, UUID memberId) {
        String occupancyKey = RedisConstants.USER_COURSE_OCCUPANCY_PREFIX + courseId + ":" + memberId;
        String stockKey = RedisConstants.COURSE_STOCK_PREFIX + courseId;

        // 점유 상태 키 삭제 및 재고 수량 복구
        redisTemplate.delete(occupancyKey);
        redisTemplate.opsForValue().increment(stockKey);
    }

    public void addToWaitingList(UUID courseId, UUID memberId) {
        String waitingKey = RedisConstants.WAITING_LIST_PREFIX + courseId;

        // 1. 이미 대기열에 있는지 확인
        Double score = redisTemplate.opsForZSet().score(waitingKey, memberId.toString());
        if (score != null) {
            throw new ServiceErrorException(OrderExceptionEnum.ERR_DUPLICATE_ORDER);
        }

        // 2. 대기열 정원 확인
        Long waitingSize = redisTemplate.opsForZSet().zCard(waitingKey);
        if (waitingSize != null && waitingSize >= OrderConstants.MAX_WAITING_SIZE) {
            throw new ServiceErrorException(OrderExceptionEnum.ERR_QUEUE_FULL);
        }

        // 3. 대기열 진입
        redisTemplate.opsForZSet().add(waitingKey, memberId.toString(), System.currentTimeMillis());
    }
}
