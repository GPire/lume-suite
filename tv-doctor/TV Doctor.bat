@echo off
cd /d "%~dp0"
where py >nul 2>nul && (py -3 tvdoctor_app.py & exit /b)
where python >nul 2>nul && (python tvdoctor_app.py & exit /b)
echo Manca Python. Premi un tasto: lo installo con Windows (winget), poi riapri questo file.
pause
winget install --id Python.Python.3.12 -e
pause
