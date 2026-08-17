// vendor.js — Long Task 시뮬레이션 (TBT / INP 감점 재현용)
// TBT(Total Blocking Time) 는 "50ms 를 넘는 long task 의 초과분" 을 합산
// 아래는 CPU 를 실제로 점유해 수백 ms 짜리 long task 하나를 만드는 코드.
(function () {
    const start = performance.now();

    // 1) 객체 3만개 직렬화/역직렬화 — 실제 번들 parse 시뮬레이션
    const big = { items: [] };
    for (let i = 0; i < 50000; i++) {
        big.items.push({
            id: i,
            name: 'vendor-item-' + i,
            tags: ['a', 'b', 'c', 'd', 'e'],
            meta: { created: Date.now(), hash: (i * 2654435761) >>> 0 }
        });
    }
    const restored = JSON.parse(JSON.stringify(big));

    // 2) CPU 바운드 루프 — 라이브러리 초기 계산 시뮬레이션
    let acc = 0;
    for (let i = 0; i < 10_000_000; i++) {
        acc = (acc + i * 2654435761) >>> 0;
    }

    window.__vendorReady = {
        size: restored.items.length,
        hash: acc,
        took: performance.now() - start  // 콘솔에서 실제 소요 시간 확인용
    };
})();