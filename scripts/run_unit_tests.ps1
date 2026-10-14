$ErrorActionPreference = "Stop"

$javaHome = "C:\Program Files\Android\Android Studio\jbr"
$java = "$javaHome\bin\java.exe"

$junit = (Get-ChildItem -Path "C:\Users\abdul\.gradle\caches" -Filter "junit-4.13.2.jar" -Recurse | Select-Object -First 1).FullName
$hamcrest = (Get-ChildItem -Path "C:\Users\abdul\.gradle\caches" -Filter "hamcrest-core-1.3.jar" -Recurse | Select-Object -First 1).FullName

$testClasses = "C:\Users\abdul\OneDrive\9831~1\divya\android\app\build\intermediates\javac\debugUnitTest\compileDebugUnitTestJavaWithJavac\classes"
$mainClasses = "C:\Users\abdul\OneDrive\9831~1\divya\android\app\build\intermediates\javac\debug\compileDebugJavaWithJavac\classes"

$cp = "$junit;$hamcrest;$testClasses;$mainClasses"

Write-Host "Running JUnit test runner on FinancialEngineTest..."
& $java -cp $cp org.junit.runner.JUnitCore com.smarttracker.core.common.FinancialEngineTest
