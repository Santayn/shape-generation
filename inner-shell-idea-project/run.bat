@echo off
setlocal
cd /d "%~dp0"
if exist out rmdir /s /q out
mkdir out
for /r src\main\java %%f in (*.java) do (
    echo %%f>> sources.txt
)
javac -encoding UTF-8 -d out @sources.txt
if errorlevel 1 goto :end
java -cp out app.InnerShellApp %*
:end
if exist sources.txt del sources.txt
endlocal
