@echo off
setlocal

rem ====== 여기 경로만 본인 환경에 맞게 확인하세요 ======
set ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe
set PKG=com.moneylog.app
rem ===================================================

set REMOTE_DIR=/sdcard/Android/data/%PKG%/files
set SRC_DIR=%~dp0db_export

echo 경고: db_export 폴더의 moneylog.db 내용으로 앱의 현재 데이터를 완전히 덮어씁니다.
echo 되돌릴 수 없으니, 계속하려면 아무 키나 누르세요. 취소하려면 창을 닫으세요.
pause

echo [1/5] 앱 완전히 종료 중...
"%ADB%" shell am force-stop %PKG%

echo [2/5] PC의 moneylog.db를 폰으로 올리는 중...
"%ADB%" push "%SRC_DIR%\moneylog.db" %REMOTE_DIR%/moneylog.db

echo [3/5] 기존 임시 WAL/SHM 파일 정리 중 (충돌 방지)...
"%ADB%" shell run-as %PKG% --user 0 rm -f databases/moneylog.db-wal databases/moneylog.db-shm

echo [4/5] 앱 전용 저장소로 옮기는 중...
"%ADB%" shell run-as %PKG% --user 0 cp %REMOTE_DIR%/moneylog.db databases/moneylog.db

echo [5/5] 폰에 남긴 임시 파일 정리 중...
"%ADB%" shell rm -f %REMOTE_DIR%/moneylog.db

echo.
echo 완료! 폰에서 MoneyLog 앱을 다시 열어서 확인해보세요.
pause
