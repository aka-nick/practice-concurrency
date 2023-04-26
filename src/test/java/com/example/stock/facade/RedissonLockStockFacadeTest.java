package com.example.stock.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import com.example.stock.domain.Stock;
import com.example.stock.repository.StockRepository;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class RedissonLockStockFacadeTest {

    @Autowired
    private RedissonLockStockFacade redissonLockStockFacade;

    @Autowired
    private StockRepository stockRepository;

    @BeforeEach
    public void before() {
        Stock stock = new Stock(1L, 100L);
        stockRepository.save(stock);
    }

    @AfterEach
    public void after() {
        stockRepository.deleteAll();
    }

    @Test
    void 동시에_100개의_요청() throws InterruptedException {
        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    redissonLockStockFacade.decrease(1L, 1L);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();

        Stock stock = stockRepository.findById(1L).orElseThrow();

        assertThat(stock.getQuantity()).isEqualTo(0L);
        /*
        첫번째 해결 - synchronized : auto commit 상태에서(@Transaction 주석처리) 경쟁상태 메서드에 synchronized 키워드 추가
        두번째 해결 - pessimistic lock : DB에 읽기 접근할 때 비관적락 전략을 사용. data jpa에서는 @Lock(value = LockModeType.PESSIMISTIC_WRITE) 처럼 사용.
        세번째 해결 - optimistic lock : DB에 업데이트를 시도할 때 version을 체크, 업데이트 실패 시 재시도하는 로직까지 작성.
        네번째 해결 - named lock : DB에서 id를 키로 하는 락객체를 얻어서 사용. 트랜잭션이 실패하더라도 락은 반환되지 않으므로 주의해서 명시적으로 반환해줄것. 또한 같은 데이터소스를 사용하면 커넥션풀을 쉽게 고갈시킬 수 있음. 또한 Transaction session을 분리해야 하는 점을 잘 이해해야 한다.
                                pessimistic lock과 비슷하지만, named lock은 분산락 구현이 용이하다. 그러나 사용상의 복잡도가 높기 때문에 잘 고려해야 한다.
        다섯번째 해결 - lettuce lock : 레디스를 활용하는 스핀락. 충돌이 잦을 경우 레디스에 부하를 줄 여지가 있다.
        여섯번째 해결 - redisson lock : pub-sub 기반의 락이기 때문에 redis에 부하를 조금 덜 줄 수 있다. 그러나 별도의 라이브러리가 필요하다.
         */
    }
}