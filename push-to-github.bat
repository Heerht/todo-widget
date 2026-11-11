@echo off
setlocal
cd /d "%~dp0"

echo.
echo ================================================
echo   Push this project to GitHub
echo ================================================
echo.
echo   1. Open https://github.com/new
echo   2. Repository name: todo-widget    Visibility: Public
echo   3. Do NOT add README / gitignore / license
echo   4. Copy the repository URL
echo.
set /p REPO=  Repository URL: 

if "%REPO%"=="" goto no_url

git remote remove origin >nul 2>&1
git remote add origin "%REPO%"
git branch -M main
echo.
echo   Pushing...
echo.
git push -u origin main

if errorlevel 1 goto push_failed

echo.
echo   Done. Open https://github.com and check the Actions tab.
echo.
pause
exit /b 0

:push_failed
echo.
echo   Push failed.
echo   If git asks for a password, paste a Personal Access Token instead:
echo     GitHub - Settings - Developer settings - Personal access tokens
echo     - Tokens classic - Generate new token - check the "repo" scope
echo.
pause
exit /b 1

:no_url
echo.
echo   No URL given, aborted.
pause
exit /b 1
