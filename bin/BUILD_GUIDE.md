# Java Build & Run Guide

## 🔧 Vấn đề Gốc Rễ (Root Cause)

Bạn gặp lỗi `java.lang.ClassNotFoundException: LoginForm` vì ba lý do chính:

### 1. **Lỗi Syntax Trong Compile Task**
- Task cũ sử dụng `cmd /c` và `&&` operator, nhưng khi chạy qua PowerShell, syntax này không hợp lệ
- Điều này làm cho code **không bao giờ được biên dịch**
- Vì vậy file `LoginForm.class` không tồn tại trong thư mục `bin/`

### 2. **Classpath Không Đúng**
- Khi compile và run, không có cách tự động thêm tất cả `.jar` từ thư mục `lib/`
- Đặc biệt là `mssql-jdbc-13.2.1.jre11.jar` không được đưa vào

### 3. **Thiếu Validation**
- Không có kiểm tra lỗi hoặc thông báo rõ ràng từ quá trình compile

## ✅ Giải Pháp Đã Áp Dụng

### 1. **Ba Script PowerShell Mới** 
   - `compile.ps1`: Biên dịch code với classpath đúng
   - `run.ps1`: Chạy ứng dụng với classpath hoàn chỉnh
   - `clean.ps1`: Xóa các file đã biên dịch cũ

### 2. **Tasks.json Được Cập Nhật**
   - Sử dụng PowerShell native thay vì `cmd /c`
   - Tự động build classpath từ thư mục `lib/`
   - Kiểm tra lỗi và in thông báo rõ ràng

### 3. **Dependency Management Tự Động**
   - Script tìm tất cả `.jar` trong `lib/` folder
   - Thêm chúng vào classpath tự động

---

## 🚀 Cách Sử Dụng

### **Tùy Chọn 1: Dùng VS Code Tasks (Khuyến Nghị)**

#### Compile:
- Nhấn `Ctrl+Shift+B` hoặc `Ctrl+P` → `Tasks: Run Build Task`

#### Run LoginForm:
- `Ctrl+Shift+D` → Chọn "Run LoginForm" từ list tasks
- Hoặc: `Ctrl+P` → `Tasks: Run Task` → `Run LoginForm`

#### Clean Build:
- `Ctrl+P` → `Tasks: Run Task` → `Clean Build`

---

### **Tùy Chọn 2: Chạy Trực Tiếp từ PowerShell Terminal**

#### Compile:
```powershell
powershell -ExecutionPolicy Bypass -File .\compile.ps1
```

#### Run:
```powershell
powershell -ExecutionPolicy Bypass -File .\run.ps1
```

#### Clean:
```powershell
powershell -ExecutionPolicy Bypass -File .\clean.ps1
```

---

## 📁 Cấu Trúc Dự Án

```
Đồ án cuối kì/
├── .vscode/
│   └── tasks.json          ← Updated: Compile & Run tasks
├── compile.ps1             ← NEW: Compile script
├── run.ps1                 ← NEW: Run script  
├── clean.ps1               ← NEW: Clean script
├── bin/                    ← Output folder (biên dịch tại đây)
│   ├── LoginForm.class
│   ├── MainMenuFrame.class
│   └── [other .class files]
├── lib/                    ← Dependencies
│   └── mssql-jdbc-13.2.1.jre11.jar
├── LoginForm.java
├── MainMenuFrame.java
└── [other .java files]
```

---

## 🔍 Output Chi Tiết

### Compile Output:
```
Step 1: Building classpath...
Step 2: Compiling Java sources...
✓ Compilation successful!

Compiled classes in:
LoginForm.class
MainMenuFrame.class
[... other classes ...]
```

### Run Output:
```
Preparing to run LoginForm...
Step 1: Building classpath...
Step 2: Compiling Java sources...
✓ Compilation successful!

Step 3: Starting LoginForm...
Classpath: bin;C:\Đồ án cuối kì\lib\mssql-jdbc-13.2.1.jre11.jar
```

---

## 🛡️ Cách Tránh Lỗi Này Trong Tương Lai

### 1. **Luôn Compile Trước Khi Run**
   - Hệ thống mới sẽ tự động compile khi chạy
   - Nếu compile fail, sẽ không chạy được

### 2. **Kiểm Tra Error Message**
   - Nếu thấy lỗi compile, đọc kỹ thông báo từ javac
   - Script sẽ dừng lại nếu có lỗi

### 3. **Add Thư Viện Mới**
   - Chỉ cần copy `.jar` vào thư mục `lib/`
   - Script sẽ tự động đưa vào classpath

### 4. **Nếu Vẫn Gặp Lỗi**
   - Chạy: `powershell -ExecutionPolicy Bypass -File .\clean.ps1`
   - Sau đó compile lại

---

## 📝 Troubleshooting

### Lỗi: "Cannot find file compile.ps1"
**Giải pháp**: Bạn đang chạy từ folder khác. Phải `cd` vào đúng folder:
```powershell
cd "c:\Đồ án cuối kì"
powershell -ExecutionPolicy Bypass -File .\compile.ps1
```

### Lỗi: "is not digitally signed"
**Giải pháp**: Chạy PowerShell với `-ExecutionPolicy Bypass` (đã có trong lệnh)

### Lỗi: "javac: command not found"
**Giải pháp**: Chưa cài Java Development Kit (JDK). Cần cài Oracle JDK hoặc OpenJDK

### Lỗi Compile: "cannot find symbol"
**Giải pháp**: 
1. Kiểm tra file có syntax error không
2. Kiểm tra dependencies trong `lib/` folder

---

## 🎯 Tóm Tắt

| Tác Vụ | Lệnh | Shortcut |
|--------|------|----------|
| Compile | `powershell -ExecutionPolicy Bypass -File .\compile.ps1` | `Ctrl+Shift+B` |
| Run | `powershell -ExecutionPolicy Bypass -File .\run.ps1` | `Ctrl+P` → Run Task |
| Clean | `powershell -ExecutionPolicy Bypass -File .\clean.ps1` | `Ctrl+P` → Clean |

**✨ Lỗi `ClassNotFoundException` đã được khắc phục hoàn toàn!**
