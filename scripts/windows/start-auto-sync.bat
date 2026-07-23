@echo off
title Nissan GTR Auto — real-time Git sync
cd /d "%~dp0..\.."
REM Poll every 5s — more reliable than FileSystemWatcher behind antivirus
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0auto-sync.ps1" -PollSeconds 5
pause
