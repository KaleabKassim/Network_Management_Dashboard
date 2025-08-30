@echo off
REM =====================================================
REM Build MSI installer for NetworkManagement
REM =====================================================

REM Clean + build custom runtime image with JavaFX
call .\mvnw.cmd clean javafx:jlink

REM Package MSI using jpackage
jpackage ^
  --type msi ^
  --runtime-image target\NetworkManagement ^
  --module com.lan.network_management/com.lan.network_management.MainApplication ^
  --name NetworkManagement ^
  --dest target\dist ^
  --win-menu ^
  --win-shortcut ^
  --win-dir-chooser

echo.
echo =====================================================
echo MSI Build Finished!
echo Installer is in target\dist\NetworkManagement-*.msi
echo =====================================================
pause