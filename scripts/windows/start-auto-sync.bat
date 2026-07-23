@echo off
title Nissan GTR Auto — real-time Git sync
cd /d "%~dp0..\.."
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0auto-sync.ps1"
pause
