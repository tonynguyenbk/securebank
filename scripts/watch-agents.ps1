# Live progress board for the Wave 1 agents (reads git only — no AI, no tokens).
# Usage (VSCode terminal):  powershell -ExecutionPolicy Bypass -File scripts\watch-agents.ps1
param([int]$IntervalSeconds = 10)

$root = 'D:\SecureBank-worktrees'
$agents = [ordered]@{
    'A  identity + gateway'          = 'identity-gateway'
    'B  banking core'                = 'banking-core'
    'C  fraud/audit/notification'    = 'event-services'
    'D  frontend'                    = 'frontend'
}

while ($true) {
    Clear-Host
    Write-Host ("SecureBank agents   {0:HH:mm:ss}   (Ctrl+C to stop)" -f (Get-Date)) -ForegroundColor Cyan
    Write-Host ('-' * 78)
    foreach ($name in $agents.Keys) {
        $path = Join-Path $root $agents[$name]
        if (-not (Test-Path $path)) { continue }
        Push-Location $path
        $commits = @(git log --oneline main..HEAD 2>$null)
        $changed = @(git status --porcelain 2>$null)
        $lastFile = Get-ChildItem -Recurse -File -ErrorAction SilentlyContinue |
            Where-Object { $_.FullName -notmatch '\\(node_modules|target|\.git|dist)\\' } |
            Sort-Object LastWriteTime -Descending | Select-Object -First 1
        $reports = Get-ChildItem -Recurse -Filter 'TEST-*.xml' -ErrorAction SilentlyContinue |
            Where-Object { $_.FullName -match '\\target\\surefire-reports\\' }
        $tests = 0; $failed = 0
        foreach ($r in $reports) {
            try {
                [xml]$x = Get-Content $r.FullName -Raw
                $tests += [int]$x.testsuite.tests
                $failed += [int]$x.testsuite.failures + [int]$x.testsuite.errors
            } catch { }
        }
        Pop-Location

        Write-Host $name -ForegroundColor Yellow -NoNewline
        Write-Host ("   commits: {0}   uncommitted: {1}" -f $commits.Count, $changed.Count)
        if ($lastFile) {
            $rel = $lastFile.FullName.Substring($path.Length + 1)
            $age = [int]((Get-Date) - $lastFile.LastWriteTime).TotalMinutes
            Write-Host ("   last edit: {0} ({1} min ago)" -f $rel, $age)
        }
        if ($tests -gt 0) {
            $color = if ($failed -gt 0) { 'Red' } else { 'Green' }
            Write-Host ("   tests: {0} run, {1} failing" -f $tests, $failed) -ForegroundColor $color
        }
        $commits | Select-Object -First 3 | ForEach-Object { Write-Host "   * $_" -ForegroundColor DarkGray }
        Write-Host ''
    }
    Start-Sleep -Seconds $IntervalSeconds
}
