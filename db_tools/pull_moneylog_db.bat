@echo off
setlocal

rem ====== 여기 경로만 본인 환경에 맞게 확인하세요 ======
set ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe
set PKG=com.moneylog.app
rem ===================================================

set REMOTE_DIR=/sdcard/Android/data/%PKG%/files
set OUT_DIR=%~dp0db_export

if not exist "%OUT_DIR%" mkdir "%OUT_DIR%"

echo [1/3] 앱 전용 DB 파일을 꺼낼 수 있는 위치로 복사 중...
"%ADB%" shell run-as %PKG% --user 0 mkdir -p files
"%ADB%" shell run-as %PKG% --user 0 cp databases/moneylog.db %REMOTE_DIR%/moneylog.db
"%ADB%" shell run-as %PKG% --user 0 cp databases/moneylog.db-wal %REMOTE_DIR%/moneylog.db-wal
"%ADB%" shell run-as %PKG% --user 0 cp databases/moneylog.db-shm %REMOTE_DIR%/moneylog.db-shm

echo [2/3] PC로 다운로드 중...
"%ADB%" pull %REMOTE_DIR%/moneylog.db "%OUT_DIR%\moneylog.db"
"%ADB%" pull %REMOTE_DIR%/moneylog.db-wal "%OUT_DIR%\moneylog.db-wal"
"%ADB%" pull %REMOTE_DIR%/moneylog.db-shm "%OUT_DIR%\moneylog.db-shm"

echo [3/3] 폰에 남긴 임시 파일 정리 중...
"%ADB%" shell rm -f %REMOTE_DIR%/moneylog.db %REMOTE_DIR%/moneylog.db-wal %REMOTE_DIR%/moneylog.db-shm

echo.
echo 완료! db_export 폴더 안의 moneylog.db를 DB Browser for SQLite로 열어보세요.
echo (moneylog.db-wal / moneylog.db-shm 파일이 없다는 에러는 무시해도 됩니다.)
pause
