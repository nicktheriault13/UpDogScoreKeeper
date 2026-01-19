# Script to fix IDE indexing issues
Write-Host "Fixing IDE cache issues..." -ForegroundColor Cyan

# Stop Gradle daemons
Write-Host "`nStopping Gradle daemons..." -ForegroundColor Yellow
.\gradlew.bat --stop

# Clean build
Write-Host "`nCleaning build..." -ForegroundColor Yellow
.\gradlew.bat clean

# Rebuild with fresh dependencies
Write-Host "`nRebuilding with fresh dependencies..." -ForegroundColor Yellow
.\gradlew.bat :composeApp:compileKotlinDesktop --refresh-dependencies

Write-Host "`n✓ Done! Now please do the following in your IDE:" -ForegroundColor Green
Write-Host "  1. File → Invalidate Caches..." -ForegroundColor White
Write-Host "  2. Check 'Invalidate and Restart'" -ForegroundColor White
Write-Host "  3. Click 'Invalidate and Restart'" -ForegroundColor White
Write-Host "`nThe Voyager import errors should disappear after the IDE restarts." -ForegroundColor Cyan
