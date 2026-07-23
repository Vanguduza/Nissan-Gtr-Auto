@echo off
title Nissan GTR - GitHub login for auto-sync
cd /d "%~dp0..\.."
set PATH=C:\Program Files\Git\cmd;%PATH%
set GCM_INTERACTIVE=always
echo.
echo Sign in to GitHub when prompted.
echo After a successful push, auto-sync can upload changes automatically.
echo.
git push -u origin HEAD
echo.
if %ERRORLEVEL%==0 (
  echo SUCCESS - GitHub auth saved. Auto-sync can push now.
) else (
  echo FAILED - try again, or create a Personal Access Token at:
  echo https://github.com/settings/tokens
)
pause
