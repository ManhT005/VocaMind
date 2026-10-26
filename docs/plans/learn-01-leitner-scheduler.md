# LEARN-01 — Kế hoạch xây dựng Leitner Scheduler

**Dự án:** VocaMind

**Owner:** Nguyễn Minh Đạo

**Reviewer theo phân công:** Thi

**Nhóm:** Domain; ưu tiên Critical

**Ước lượng gốc:** 8 giờ, D1–D3 của Tuần 1

**Nhánh:** `feature/learn-01-leitner-scheduler`

**Base:** `develop`

## 1. Căn cứ

- Bảng phân công, LEARN-01: LeitnerScheduler Java thuần; box 0..5;
  REMEMBERED/HARD/FORGOT; dueAt theo ngày lịch; leech threshold=4. Pure domain
  service nhận current state + rating + fixed time, trả ScheduleResult; không
  Android API/DB. Thiết kế deterministic để chạy đủ TC-SCHED-01..10.
- Yêu cầu tham chiếu: FR-VM-028..033; BR-03..06; AC-07; ADR-02; DEC-11/12/15.
- DATA-02 (nhánh `feature/data-02-user-database`): UserWordProgress có `box`,
  `dueAt` (epoch ms), `consecutiveMistakes`, `isLeech`; ReviewEvent có `rating`
  là chuỗi REMEMBERED/HARD/FORGOT, `boxBefore`, `boxAfter`, `nextDueAt`.
- DATA-03: `dueProgress` lọc `dueAt <= now`.

## 2. Quy tắc lập lịch (đã chốt cho LEARN-01)

Khoảng ôn theo chuỗi Leitner phổ biến 1-3-7-14-30 ngày (1 tuần, 2 tuần, 1 tháng).
Tất cả nằm trong hằng số của `LeitnerScheduler`; khi có SRS chỉ cần chỉnh hằng số.

| Rating | Box sau | consecutiveMistakes | isLeech |
|---|---|---|---|
| REMEMBERED | min(box + 1, 5) | 0 | false |
| HARD | giữ nguyên box | 0 | false |
| FORGOT | 0 | +1 | true nếu ≥ 4, nếu không giữ nguyên |

Khoảng ôn theo box sau khi chấm (đơn vị: ngày lịch):

| Box | 0 | 1 | 2 | 3 | 4 | 5 |
|---|---|---|---|---|---|---|
| Số ngày | 1 | 1 | 3 | 7 | 14 | 30 |

- `dueAt` = thời điểm 00:00 (đầu ngày) theo `ZoneId` của ngày `hôm nay + N`,
  đổi sang epoch milliseconds. Vì vậy từ đến hạn trong cả ngày đó với điều kiện
  `dueAt <= now`, kể cả khi đổi giờ DST.
- FORGOT luôn đến hạn ngày mai (box 0 = 1 ngày); việc học lại trong phiên thuộc
  TODAY-02, không thay đổi dueAt.
- Từ mới bắt đầu ở box 0, mistakes 0, không leech.

## 3. Thiết kế

```text
app/src/main/java/com/vocamind/app/domain/learning/
  Rating.java          enum REMEMBERED, HARD, FORGOT
  LeitnerState.java    box, consecutiveMistakes, isLeech; newWord()
  ScheduleResult.java  boxBefore, boxAfter, dueAt, consecutiveMistakes, isLeech
  LeitnerScheduler.java schedule(state, rating, nowEpochMillis, zone)
```

- Chỉ dùng Java standard library (`java.time` có từ API 26). Không import
  android.*, androidx.*, Room hay lớp data/feature.
- Class bất biến, kiểm tra tham số (box 0..5, mistakes ≥ 0, không null).
- Thời gian truyền vào làm tham số nên test luôn deterministic.

## 4. Ranh giới với task sau

| Trong LEARN-01 | Thuộc task sau |
|---|---|
| Thuật toán box/dueAt/leech thuần domain | DATA-06: ghi ReviewEvent + UserWordProgress trong một transaction |
| Unit test cơ bản cho từng rating | LEARN-02: bộ TC-SCHED-01..10 và boundary |
| Cờ leech trong kết quả | LEARN-03: hiển thị "Từ hay quên" và test tích hợp |
| — | TODAY-01/02: planner và hàng đợi học lại |

## 5. Kiểm chứng

- `./gradlew testDebugUnitTest` và `./gradlew assembleDebug` (giống CI).
- `git grep "import android" app/src/main/java/com/vocamind/app/domain/learning`
  không có kết quả.
