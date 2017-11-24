@echo off

echo %*
md ..\results\1 ..\report
echo Foo > ..\results\1\foo.txt
copy /y testreport.xml ..\report\testreport.xml
