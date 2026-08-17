// 가장 단순한 API 호출 — 로그인 + 일기 목록
// ※ emotiondiary 는 토큰을 Authorization 헤더가 아닌 JSON body 로 내려줌
//   (/api/auth/login 응답 = { accessToken, refreshToken, tokenType, accessTokenExpiresIn })
// ※ /api/diaries 는 from / to (epoch ms) 쿼리 파라미터 필수, 응답은 { items, total } 래핑
(async () => {
    const status = document.getElementById('status');
    const list = document.getElementById('list');

    // Date → YYYYMMDD 정수 (예: 2026-04-23 → 20260423)
    const toYmd = (d) => {
        const y = d.getFullYear();
        const m = String(d.getMonth() + 1).padStart(2, '0');
        const day = String(d.getDate()).padStart(2, '0');
        return Number(`${y}${m}${day}`);
    };
    // YYYYMMDD 정수 → 표시용 문자열 (예: 20260423 → "2026-04-23")
    const fmtYmd = (n) => {
        const s = String(n);
        return `${s.slice(0, 4)}-${s.slice(4, 6)}-${s.slice(6, 8)}`;
    };

    try {
        // 1) 로그인 — 고정 계정(실습 전 /api/auth/signup 으로 생성해둘 것)
        const loginRes = await fetch('/api/auth/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email: 'demo@test.com', password: 'pw12345!' })
        });
        if (!loginRes.ok) throw new Error('로그인 실패 (' + loginRes.status + ')');

        const { accessToken, tokenType } = await loginRes.json();
        const authHeader = `${tokenType} ${accessToken}`;  // "Bearer eyJ..."

        // 2) 최근 1년치 일기 목록 조회 — from/to 는 YYYYMMDD 정수
        const now = new Date();
        const yearAgo = new Date(now.getFullYear() - 1, now.getMonth(), now.getDate());
        const from = toYmd(yearAgo);
        const to   = toYmd(now);
        const listRes = await fetch(`/api/diaries?from=${from}&to=${to}&sort=latest`, {
            headers: { 'Authorization': authHeader }
        });
        if (!listRes.ok) throw new Error('목록 조회 실패 (' + listRes.status + ')');

        const data = await listRes.json();  // { items: [...], total: N }

        // 3) 화면 갱신 — DiaryResponse 필드는 id / date / content / emotionId
        status.textContent = `로그인 완료 — 일기 ${data.total}개`;
        list.innerHTML = (data.items || []).slice(0, 20)
            .map(d => `<li>
                <strong>${fmtYmd(d.date)}</strong>
                <span> · emotion ${d.emotionId ?? '-'}</span>
                <br/>${d.content ?? ''}
            </li>`)
            .join('');
    } catch (e) {
        status.textContent = '오류: ' + e.message;
        console.error(e);
    }
})();