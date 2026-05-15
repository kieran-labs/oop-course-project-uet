# Hướng dẫn cài đặt và chạy

Tài liệu này dành cho người **clone repo về máy lần đầu** và muốn chạy được cả server lẫn client. Mọi lệnh dưới đây là cho **Windows**; macOS/Linux thay `gradlew.bat` bằng `./gradlew` và `set` bằng `export`.

---

## 1. Cài đặt môi trường

### Bắt buộc

| Phần mềm | Phiên bản tối thiểu | Kiểm tra |
|---|---|---|
| **Git** | 2.x | `git --version` |
| **JDK 21** | 21.0.x | `java -version` |

> **Lưu ý**: Project pin Java 21 trong `build.gradle.kts` qua `JavaLanguageVersion.of(21)`. JDK 17 hoặc 22+ sẽ không compile được.

### Không cần cài

- **PostgreSQL** — project dùng **Embedded PostgreSQL**, tự khởi động khi server chạy. Dữ liệu lưu ở `data/postgres/`.
- **Gradle** — repo có sẵn Gradle Wrapper (`gradlew.bat`).
- **Maven** — không dùng Maven.

### Khuyến nghị

- **JDK Eclipse Temurin 21** (miễn phí, OpenJDK chính thức): https://adoptium.net/

Sau khi cài, kiểm tra:

```cmd
java -version
:: java version "21.0.x" 2024-xx-xx LTS

javac -version
:: javac 21.0.x
```

Nếu bạn cài nhiều JDK, set `JAVA_HOME` trỏ vào JDK 21:

```cmd
setx JAVA_HOME "C:\Program Files\Eclipse Adoptium\jdk-21.0.x-hotspot"
```

---

## 2. Clone repo

```cmd
git clone https://github.com/kieran-labs/oop-course-project-uet.git
cd oop-course-project-uet
```

---

## 3. Cấu hình `JWT_SECRET` (BẮT BUỘC)

Server **sẽ fail-fast** nếu thiếu biến môi trường `JWT_SECRET` hoặc nếu chuỗi ngắn hơn 32 bytes UTF-8.

> **Tại sao?** Đây là security best practice — secret ký JWT không bao giờ được hardcode trong source code, nếu không bất kỳ ai clone repo cũng có thể forge token admin.

### Cách 1 — set tạm thời cho 1 terminal session

**cmd.exe:**
```cmd
set JWT_SECRET=replace-with-a-random-secret-of-at-least-32-bytes
```

**PowerShell:**
```powershell
$env:JWT_SECRET = "replace-with-a-random-secret-of-at-least-32-bytes"
```

Biến này chỉ tồn tại trong terminal hiện tại. Mở terminal mới phải set lại.

### Cách 2 — set vĩnh viễn cho User (khuyên dùng cho dev)

```cmd
setx JWT_SECRET "replace-with-a-random-secret-of-at-least-32-bytes"
```

Sau lệnh này, **mở terminal mới** thì `JWT_SECRET` đã có sẵn (terminal hiện tại vẫn chưa thấy — phải đóng và mở lại).

### Tạo secret ngẫu nhiên

Nếu không muốn tự nghĩ chuỗi 32 ký tự, dùng PowerShell:

```powershell
[Convert]::ToBase64String((1..32 | ForEach-Object {Get-Random -Maximum 256}))
```

Copy output rồi `setx JWT_SECRET "<output>"`.

---

## 4. Chạy server (3 cách)

Đảm bảo bạn đang ở thư mục gốc của repo (`oop-course-project-uet/`).

### Cách A — chạy trực tiếp bằng Gradle (recommended cho dev)

```cmd
gradlew.bat run
```

Lần đầu sẽ tải dependencies (~vài phút). Đợi đến khi thấy log:

```
Javalin started in XXX ms.
```

Server đã sẵn sàng ở **http://localhost:8080**.

> **Dừng server**: nhấn `Ctrl+C` trong terminal đang chạy gradle.

### Cách B — dùng script `.bat` (chạy background, có log file)

```cmd
server-start.bat
```

Script này tự build JAR (nếu chưa có), chạy server **ẩn** (không có cửa sổ console), lưu PID vào `data/launcher.pid`, log ra `logs/server.out.log` và `logs/server.err.log`.

Để dừng:

```cmd
server-stop.bat
```

Để kiểm tra trạng thái:

```cmd
server-status.bat
```

> **Lưu ý**: Vẫn cần set `JWT_SECRET` trước khi chạy `server-start.bat`. Script không tự set secret.

### Cách C — chạy từ JAR prebuilt

Nếu bạn đã có file `auction-server-1.0.0.jar` (từ release hoặc build local):

```cmd
:: Build JAR từ source nếu chưa có
gradlew.bat shadowJar

:: Chạy
java -jar build\libs\auction-server-1.0.0.jar
```

---

## 5. Chạy client JavaFX (terminal khác)

Mở **một terminal mới** (vì server đang chiếm terminal cũ), `cd` vào repo:

```cmd
cd oop-course-project-uet
gradlew.bat runClient
```

Cửa sổ JavaFX sẽ mở ra với màn hình login.

> **Lưu ý**: Client **không** cần `JWT_SECRET`. Chỉ server mới cần.

### Chạy nhiều client cùng lúc (test concurrent bidding)

Mở 3–4 terminal, mỗi terminal chạy `gradlew.bat runClient`. Đăng nhập với tài khoản khác nhau để test đặt giá đồng thời.

---

## 6. Tài khoản mặc định

Khi server khởi động lần đầu, nó tự tạo tài khoản admin:

| Tài khoản | Username | Password | Role |
|---|---|---|---|
| Admin | `admin` | `123456` | ADMIN |

Để tạo tài khoản BIDDER/SELLER: dùng nút **Đăng ký** trên màn hình login của client.

---

## 7. Lỗi thường gặp

### `JWT_SECRET is required and must be at least 32 bytes long`

Bạn chưa set `JWT_SECRET` hoặc set giá trị quá ngắn. Quay lại **mục 3**.

### `Port 8080 already in use`

Server cũ chưa tắt. Chạy `server-stop.bat` hoặc tìm process trên port 8080:

```cmd
netstat -ano | findstr :8080
taskkill /PID <PID> /F
```

### `Address already in use: bind` ở port khác (5432, 5433…)

Embedded PostgreSQL đang xung đột với PostgreSQL đã cài. Nếu bạn cài PostgreSQL từ trước trên máy, tắt service Windows của nó:

```cmd
net stop postgresql-x64-16
```

### Lần đầu chạy mất rất lâu

Bình thường — Gradle phải tải ~200MB dependencies. Lần sau sẽ nhanh hơn (đã cache trong `~/.gradle/`).

### JavaFX client báo `Module javafx.controls not found`

Không nên xảy ra vì project dùng `org.openjfx.javafxplugin`. Nhưng nếu xảy ra, kiểm tra:

```cmd
gradlew.bat --version
:: Phải hiển thị Gradle 8.x và JVM 21.x
```

Nếu JVM hiển thị 17, set lại `JAVA_HOME` (xem **mục 1**).

### Database lỗi không thể recover

Reset database (xóa toàn bộ dữ liệu):

```cmd
db-reset.bat
```

Lần chạy server kế tiếp sẽ tự tạo schema mới qua Flyway migrations + seed lại tài khoản admin.

---

## 8. Build prebuilt JAR

### Build server JAR (~100MB, có shaded dependencies)

```cmd
gradlew.bat shadowJar
:: Output: build\libs\auction-server-1.0.0.jar
```

### Build client JAR

```cmd
gradlew.bat shadowClient
:: Output: build\libs\auction-client-1.0.0.jar
```

### Chạy JAR

```cmd
:: Server — vẫn cần JWT_SECRET
set JWT_SECRET=replace-with-a-random-secret-of-at-least-32-bytes
java -jar build\libs\auction-server-1.0.0.jar

:: Client (terminal khác)
java -jar build\libs\auction-client-1.0.0.jar
```

---

## 9. Chạy test

```cmd
:: Toàn bộ test suite + check (Spotless, Checkstyle, SpotBugs, Jacoco)
gradlew.bat clean test check jacocoTestReport
```

Báo cáo coverage: `build/reports/jacoco/test/html/index.html`.

---

## 10. Cấu trúc thư mục data

Sau khi chạy lần đầu, repo sẽ có thêm các thư mục:

| Thư mục | Mô tả | Ignore trong git? |
|---|---|---|
| `data/postgres/` | Embedded PostgreSQL data files | ✅ |
| `data/launcher.pid` | PID của process java (do `server-start.bat`) | ✅ |
| `data/server.pid` | PID do app self-register | ✅ |
| `data/server.token` | Random token cho `/internal/shutdown` | ✅ |
| `logs/` | Stdout/stderr của server khi chạy qua `.bat` | ✅ |
| `build/` | Gradle output (compiled classes, JARs, reports) | ✅ |

Tất cả đã được liệt kê trong `.gitignore` — không cần lo việc commit nhầm.

---

## 11. Workflow điển hình

```cmd
:: Lần đầu sau khi clone
cd oop-course-project-uet
setx JWT_SECRET "my-local-dev-secret-at-least-32-bytes-long-x"
:: → đóng terminal, mở lại

:: Mỗi lần dev
gradlew.bat run                          :: Terminal 1: server
gradlew.bat runClient                    :: Terminal 2: client
gradlew.bat runClient                    :: Terminal 3: client thứ 2 để test

:: Khi xong việc
:: Ctrl+C ở Terminal 1 (hoặc server-stop.bat)
```

---

## 12. Tham khảo thêm

- **Business rules**: `docs/BUSINESS_RULES.md`
- **Database schema**: `docs/SCHEMA.md`
- **README chính**: `README.md` (kiến trúc, design patterns, API endpoints)
- **Audit findings & fixes**: `AUDIT_REPORT.md`, `FIX_TASKS.md`

Nếu gặp lỗi không có trong mục 7, mở issue trên GitHub kèm:
- Output của `java -version`
- Output của `gradlew.bat --version`
- Log lỗi (10–20 dòng cuối)
- Bước tái hiện
