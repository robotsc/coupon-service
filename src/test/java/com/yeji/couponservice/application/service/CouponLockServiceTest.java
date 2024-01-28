package com.yeji.couponservice.application.service;

import static org.junit.Assert.assertEquals;

import com.yeji.couponservice.adapter.out.persistence.CreateCouponPortAdapter;
import com.yeji.couponservice.application.port.in.CouponResponse;
import com.yeji.couponservice.domain.Coupon;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;


@SpringBootTest
class CouponLockServiceTest {

    @Autowired
    private CreateCouponPortAdapter createCouponPortAdapter;

    @Autowired
    private CouponLockService couponLockService;

    @BeforeEach
    public void setUp() throws Exception {
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd");

        Coupon coupon = Coupon.builder()
                              .couponName("선착순 100명 10프로 할인")
                              .couponCode("네고왕")
                              .numberOfCoupons(1000)
                              .downloadStartDate(LocalDate.parse("20240120", dateTimeFormatter))
                              .downloadEndDate(LocalDate.parse("20240131", dateTimeFormatter))
                              .availableStartDate(LocalDate.parse("20240120", dateTimeFormatter))
                              .availableEndDate(LocalDate.parse("20240131", dateTimeFormatter))
                              .build();

        createCouponPortAdapter.save(coupon);
    }


    @Test
    @DisplayName("쿠폰 발급 동시성 테스트")
    void test_case_1() throws Exception {
        // given
        List<Coupon> coupons = createCouponPortAdapter.getCoupons();
        if (coupons.isEmpty()) {
            throw new IllegalStateException();
        }
        int threadCount = 100;
        UUID couponId = coupons.get(0)
                               .getCouponId();
        // when
        ExecutorService executor = Executors.newFixedThreadPool(5);
        CountDownLatch countDownLatch = new CountDownLatch(threadCount);
        // then
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    CouponResponse couponResponse = couponLockService.issuanceCouponWithConcurrent(couponId.toString());
                    System.out.println(+couponResponse.getNumberOfCoupons());
                } finally {
                    countDownLatch.countDown();
                }

            });
        }
        countDownLatch.await();

        Optional<Coupon> optionalCoupon = createCouponPortAdapter.findById(couponId.toString());
        if (optionalCoupon.isEmpty()) {
            throw new IllegalStateException();
        }

        Coupon coupon = optionalCoupon.get();
        assertEquals(900, coupon.getNumberOfCoupons());
    }

    @Test
    @DisplayName("쿠폰 발급 동시성 테스트 - optimisticLock")
    void test_case_2() throws Exception {
        // given
        List<Coupon> coupons = createCouponPortAdapter.getCoupons();
        if (coupons.isEmpty()) {
            throw new IllegalStateException();
        }
        int threadCount = 100;
        UUID couponId = coupons.get(0)
                               .getCouponId();
        // when
        ExecutorService executor = Executors.newFixedThreadPool(32);
        CountDownLatch countDownLatch = new CountDownLatch(threadCount);
        // then
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    CouponResponse couponResponse = couponLockService.issuanceCouponWithOptimisticLock(couponId.toString());
                    System.out.println("couponResponse: " + couponResponse.getNumberOfCoupons());
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } finally {
                    countDownLatch.countDown();
                }

            });
        }
        countDownLatch.await();

        Optional<Coupon> optionalCoupon = createCouponPortAdapter.findById(couponId.toString());
        if (optionalCoupon.isEmpty()) {
            throw new IllegalStateException();
        }

        Coupon coupon = optionalCoupon.get();
        assertEquals(900, coupon.getNumberOfCoupons());
    }
}