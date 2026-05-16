# Hướng dẫn cài đặt và chạy

Tài liệu này dành cho người **clone repo về máy lần đầu** và muốn chạy được cả server lẫn client. Mọi lệnh dưới đây ưu tiên **Windows**; với macOS/Linux, thay `gradlew.bat` bằng `./gradlew` và thay `set`/`setx` bằng `export`.

---

## 1. Cài đặt môi trường

### Bắt buộc

| Phần mềm | Phiên bản tối thiểu | Kiểm tra |
|---|---|---|
| **Git** | 2.x | `git --version` |
| **JDK 21** | 21.x | `java -version` và `javac -version` |

> Project dùng Gradle Java Toolchain với `JavaLanguageVersion.of(21)`. Cách ít lỗi nhất là cài JDK 21 và trỏ `JAVA_HOME` vào JDK 21. JDK 17 hoặc thấp hơn không phù hợp; JDK mới hơn có thể chạy Gradle, nhưng build vẫn cần Gradle tìm được toolchain Java 21.

### Không cần cài cho chế độ mặc định

- **PostgreSQL** — mặc định project dùng Embedded PostgreSQL, tự khởi động khi server chạy. Dữ liệu lưu ở `data/postgres/`.
- **Gradle** — repo có sẵn Gradle Wrapper (`gradlew.bat` / `gradlew`).
- **Maven** — project không dùng Maven.

> Nâng cao: nếu set `DB_URL`, server sẽ dùng PostgreSQL bên ngoài thay vì Embedded PostgreSQL. Khi đó có thể set thêm `DB_USER` và `DB_PASSWORD`; Flyway vẫn chạy cùng bộ migrations.

### Khuyến nghị

- **Eclipse Temurin JDK 21**: https://adoptium.net/

Sau khi cài, kiểm tra:

```cmd
java -version
:: expected: version 21.x

javac -version
:: expected: javac 21.x
```

Nếu bạn cài nhiều JDK, set `JAVA_HOME` trỏ vào JDK 21:

```cmd
setx JAVA_HOME "C:\Program Files\Eclipse Adoptium\jdk-21.x.x-hotspot"
```

Sau `setx`, đóng terminal cũ và mở terminal mới.

---

## 2. Clone repo

```cmd
git clone https://github.com/kieran-labs/oop-course-project-uet.git
cd oop-course-project-uet
```

---

## 3. Cấu hình `JWT_SECRET` (BẮT BUỘC)

Server **fail-fast** nếu thiếu `JWT_SECRET`, nếu giá trị rỗng, hoặc nếu chuỗi ngắn hơn 32 bytes khi encode bằng UTF-8.

> Secret ký JWT không được hardcode trong source code. Nếu secret bị public, người khác có thể giả mạo token.

### Cách 1 — set tạm thời cho một terminal session

**cmd.exe:**

```cmd
set JWT_SECRET=replace-with-a-random-secret-of-at-least-32-bytes
```

**PowerShell:**

```powershell
$env:JWT_SECRET = "replace-with-a-random-secret-of-at-least-32-bytes"
```

Biến này chỉ tồn tại trong terminal hiện tại. Mở terminal mới thì phải set lại.

### Cách 2 — set vĩnh viễn cho User

```cmd
setx JWT_SECRET "replace-with-a-random-secret-of-at-least-32-bytes"
```

Sau lệnh này, đóng terminal hiện tại và mở terminal mới.

### Tạo secret ngẫu nhiên

**PowerShell:**

```powershell
[Convert]::ToBase64String((1..32 | ForEach-Object { Get-Random -Minimum 0 -Maximum 256 }))
```

Copy output rồi set vào `JWT_SECRET`.

> File `.env.example` chỉ là mẫu tham khảo. Ứng dụng hiện đọc `JWT_SECRET` từ environment variable thật qua `System.getenv(...)`; `.env` ở repo root không được auto-load bởi server.

---

## 4. Chạy server

Đảm bảo bạn đang ở thư mục gốc của repo (`oop-course-project-uet/`) và terminal hiện tại đã có `JWT_SECRET`.

### Cách A — chạy trực tiếp bằng Gradle (khuyên dùng khi dev)

```cmd
gradlew.bat run
```

Lần đầu Gradle sẽ tải dependencies. Đợi đến khi thấy log dạng:

```text
Javalin started in <N> ms
```

Server sẵn sàng ở `http://localhost:8080`.

Kiểm tra nhanh:

```cmd
curl http://localhost:8080/api/health
```

Dừng server bằng `Ctrl+C` trong terminal đang chạy Gradle.

### Cách B — dùng script `.bat` (chạy background, có log file)

```cmd
server-start.bat
```

Script này:

- kiểm tra `/api/health` để tránh start trùng server;
- build server JAR bằng `shadowJar` nếu chưa có JAR;
- chạy server ẩn bằng `java -jar`;
- ghi launcher PID vào `data/launcher.pid`;
- ghi log vào `logs/server.out.log` và `logs/server.err.log`;
- chờ `/api/health` báo `ok`.

Dừng server:

```cmd
server-stop.bat
```

Kiểm tra trạng thái:

```cmd
server-status.bat
```

> Vẫn cần set `JWT_SECRET` trước khi chạy `server-start.bat`. Script không tự tạo hoặc tự set secret.

### Cách C — chạy từ JAR local hoặc release

Nếu bạn đã có `auction-server-1.0.0.jar`:

```cmd
set JWT_SECRET=replace-with-a-random-secret-of-at-least-32-bytes
java -jar auction-server-1.0.0.jar
```

Nếu muốn build server JAR từ source trước:

```cmd
gradlew.bat shadowJar
java -jar build\libs\auction-server-1.0.0.jar
```

---

## 5. Chạy client JavaFX

Mở terminal khác, `cd` vào repo, rồi chạy:

```cmd
gradlew.bat runClient
```

Cửa sổ JavaFX sẽ mở ra.

> Client không cần `JWT_SECRET`; chỉ server cần secret để ký/verify JWT.

### Chạy nhiều client cùng lúc

Mở nhiều terminal và chạy `gradlew.bat runClient` ở mỗi terminal. Đăng nhập bằng các tài khoản khác nhau để test bidding/concurrent UI updates.

Nếu đã có client JAR:

```cmd
java -jar auction-client-1.0.0.jar
```

Hoặc nếu build local:

```cmd
java -jar build\libs\auction-client-1.0.0.jar
```

---

## 6. Tài khoản mặc định

Khi server khởi động, `App.seedAdminIfNeeded()` tạo tài khoản admin nếu chưa tồn tại:

| Tài khoản | Username | Password | Role |
|---|---|---|---|
| Admin | `admin` | `123456` | ADMIN |

Để tạo tài khoản BIDDER/SELLER, dùng nút **Đăng ký** trên màn hình login/register của client.

---

## 7. Lỗi thường gặp

### `JWT_SECRET is required and must be at least 32 bytes long`

Bạn chưa set `JWT_SECRET`, set trong terminal khác, hoặc set giá trị quá ngắn. Quay lại [mục 3](#3-cấu-hình-jwt_secret-bắt-buộc).

### `Port 8080 already in use` hoặc `Server is already running at http://localhost:8080`

Server cũ chưa tắt hoặc process khác đang chiếm port 8080.

```cmd
server-status.bat
server-stop.bat
```

Hoặc kiểm tra thủ công:

```cmd
netstat -ano | findstr :8080
taskkill /PID <PID> /F
```

### Server start bằng `.bat` nhưng không healthy

Xem log:

```cmd
type logs\server.out.log
type logs\server.err.log
```

Nguyên nhân phổ biến nhất là terminal chạy `server-start.bat` chưa có `JWT_SECRET`.

### Embedded PostgreSQL / database bị kẹt sau khi kill process

Nếu server bị kill đột ngột, `data/postgres/` hoặc PID file có thể còn trạng thái cũ. Reset database nếu bạn chấp nhận xóa dữ liệu local:

```cmd
db-reset.bat
```

Lần chạy server kế tiếp sẽ tạo lại embedded database và chạy Flyway migrations từ đầu.

### Lần đầu chạy mất lâu

Bình thường. Gradle cần tải dependencies, và Embedded PostgreSQL có thể tải/cached binary lần đầu. Các lần sau sẽ nhanh hơn nhờ cache trong `~/.gradle/` và cache của embedded-postgres.

### JavaFX client báo `Module javafx.controls not found`

Project dùng `org.openjfx.javafxplugin`, nên lỗi này thường xuất hiện khi JVM/toolchain không đúng hoặc dependency chưa resolve xong. Kiểm tra:

```cmd
gradlew.bat --version
```

Đảm bảo JVM/Toolchain là Java 21, rồi chạy lại:

```cmd
gradlew.bat clean runClient
```

---

## 8. Build JAR

### Build cả server và client JAR

```cmd
gradlew.bat clean buildJars
```

Output:

```text
build\libs\auction-server-1.0.0.jar
build\libs\auction-client-1.0.0.jar
```

### Build riêng từng JAR

```cmd
gradlew.bat shadowJar      :: server JAR
gradlew.bat shadowClient   :: client JAR
```

### Chạy JAR sau khi build

```cmd
:: Server — vẫn cần JWT_SECRET
set JWT_SECRET=replace-with-a-random-secret-of-at-least-32-bytes
java -jar build\libs\auction-server-1.0.0.jar

:: Client — terminal khác
java -jar build\libs\auction-client-1.0.0.jar
```

---

## 9. Chạy test và quality checks

```cmd
:: Toàn bộ test suite + quality gates

gradlew.bat clean test check jacocoTestReport
```

Báo cáo coverage:

```text
build/reports/jacoco/test/html/index.html
```

SpotBugs report:

```text
build/reports/spotbugs/spotbugsMain.html
```

---

## 10. Cấu trúc thư mục sinh ra khi chạy

| Thư mục / file | Mô tả | Ignore trong git? |
|---|---|---|
| `data/postgres/` | Embedded PostgreSQL data directory | Có |
| `data/postgres.pid` | PID của embedded PostgreSQL | Có |
| `data/launcher.pid` | PID của process do `server-start.bat` tạo | Có |
| `data/server.pid` | PID do app ghi khi server chạy | Có |
| `data/server.token` | Random token dùng cho `/internal/shutdown` local-only | Có |
| `logs/` | Stdout/stderr khi chạy server bằng `.bat` | Có |
| `build/` | Gradle output: classes, JARs, reports | Có |
| `.gradle/` | Gradle local cache trong repo | Có |

---

## 11. Workflow điển hình

```cmd
:: Lần đầu sau khi clone
cd oop-course-project-uet
setx JWT_SECRET "my-local-dev-secret-at-least-32-bytes-long-x"
:: đóng terminal, mở lại

:: Terminal 1: server
gradlew.bat run

:: Terminal 2: client
gradlew.bat runClient

:: Terminal 3: client thứ hai để test realtime bidding
gradlew.bat runClient
```

Khi xong việc, dừng server bằng `Ctrl+C` ở terminal server hoặc dùng `server-stop.bat` nếu server được start bằng script.

---

## 12. Tham khảo thêm

- **Business rules**: `docs/BUSINESS_RULES.md`
- **Database schema**: `docs/SCHEMA.md`
- **README chính**: `README.md` — overview, architecture, design patterns, API endpoints

Nếu gặp lỗi không có trong mục 7, mở issue trên GitHub kèm:

- Output của `java -version`
- Output của `javac -version`
- Output của `gradlew.bat --version`
- Log lỗi 10–20 dòng cuối
- Các bước tái hiện
