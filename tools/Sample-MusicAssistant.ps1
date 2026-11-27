<#
Samples the Music Assistant websocket API and saves REDACTED results, so Downbeat's
models can be checked against a real server. Run it yourself; Claude never sees your token.

  $env:MA_URL = 'https://your-server'        # no trailing slash, kept out of the repo
  .\tools\Sample-MusicAssistant.ps1           # prompts for a token (input hidden)

Make a token in the Music Assistant web UI (profile > long-lived token), or leave it
to this script's login mode: set $env:MA_USER and it will prompt for the password,
log in, create a long-lived token named "downbeat-probe" and print only its id.
Read-only except that optional token creation. Output: %TEMP%\downbeat-samples
#>
$ErrorActionPreference = 'Stop'
if (-not $env:MA_URL) { throw 'Set $env:MA_URL first.' }
$base = $env:MA_URL.TrimEnd('/')
$ws = New-Object System.Net.WebSockets.ClientWebSocket
$ws.ConnectAsync([Uri](($base -replace '^http','ws') + '/ws'), [Threading.CancellationToken]::None).Wait()
$script:id = 0
function Receive-Msg {
  $buf = New-Object byte[] 65536; $sb = New-Object Text.StringBuilder
  do {
    $seg = New-Object ArraySegment[byte] -ArgumentList (,$buf)
    $r = $ws.ReceiveAsync($seg, [Threading.CancellationToken]::None).Result
    [void]$sb.Append([Text.Encoding]::UTF8.GetString($buf, 0, $r.Count))
  } until ($r.EndOfMessage)
  $sb.ToString()
}
function Send-Cmd($cmd, $cmdArgs) {
  $script:id++
  $o = @{ command = $cmd; message_id = $script:id }
  if ($cmdArgs) { $o.args = $cmdArgs }
  $bytes = [Text.Encoding]::UTF8.GetBytes(($o | ConvertTo-Json -Depth 8 -Compress))
  $seg = New-Object ArraySegment[byte] -ArgumentList (,$bytes)
  $ws.SendAsync($seg, 'Text', $true, [Threading.CancellationToken]::None).Wait()
  while ($true) {
    $m = Receive-Msg | ConvertFrom-Json
    if ($m.message_id -eq $script:id) { return $m }   # events in between are skipped
  }
}
function Redact([string]$t) {
  $t = $t -replace '\b\d{1,3}(\.\d{1,3}){3}\b', 'x.x.x.x'
  $t = $t -replace 'https?://[^/"\s]+', 'https://media.example.com'
  $t
}
$out = Join-Path $env:TEMP 'downbeat-samples'
New-Item -ItemType Directory -Force $out | Out-Null

$first = Receive-Msg                         # server info is sent first
Set-Content "$out\00-server-info.json" (Redact $first)

$token = $null
if ($env:MA_USER) {
  $pw = Read-Host 'Password' -AsSecureString
  $plain = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($pw))
  $login = Send-Cmd 'auth/login' @{ username = $env:MA_USER; password = $plain; provider_id = 'builtin'; device_name = 'downbeat-probe' }
  $plain = $null
  $token = $login.result.access_token; if (-not $token) { $token = $login.result.token }
  $keys = ($login | ConvertTo-Json -Depth 4 -Compress) -replace '"(access_)?token":"[^"]*"', '"token":"REDACTED"'
  Set-Content "$out\01-login-shape.json" (Redact $keys)
} else {
  $sec = Read-Host 'Token' -AsSecureString
  $token = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($sec))
}
$a = Send-Cmd 'auth' @{ token = $token }
Set-Content "$out\02-auth-reply.json" (Redact (($a | ConvertTo-Json -Depth 4 -Compress) -replace '"token":"[^"]*"', '"token":"REDACTED"'))

$samples = [ordered]@{
  '10-players-all'      = @('players/all', $null)
  '11-queues-all'       = @('player_queues/all', $null)
  '12-recently-played'  = @('music/recently_played_items', @{ limit = 5 })
  '13-albums'           = @('music/albums/library_items', @{ limit = 3 })
  '14-search'           = @('music/search', @{ search_query = 'a'; limit = 3 })
  '15-me'               = @('auth/me', $null)
}
foreach ($k in $samples.Keys) {
  $r = Send-Cmd $samples[$k][0] $samples[$k][1]
  Set-Content "$out\$k.json" (Redact ($r | ConvertTo-Json -Depth 12))
  "{0}: {1}" -f $k, $(if ($r.error_code) { "ERROR $($r.error_code)" } else { 'ok' })
}

# Error reply shape: ask for a command that does not exist, and for a player that does not exist.
$e1 = Send-Cmd 'downbeat/not_a_command' $null
Set-Content "$out\20-error-unknown-command.json" (Redact ($e1 | ConvertTo-Json -Depth 6))
$e2 = Send-Cmd 'players/get' @{ player_id = 'does-not-exist' }
Set-Content "$out\21-error-unknown-player.json" (Redact ($e2 | ConvertTo-Json -Depth 6))

# Optional: create a long-lived token to see the result shape. Only the shape is saved (token value redacted).
if ($env:MA_MAKE_TOKEN -eq '1') {
  $t = Send-Cmd 'auth/token/create' @{ name = 'downbeat-probe-2' }
  $shape = ($t | ConvertTo-Json -Depth 6) -replace '"result":\s*"[^"]*"', '"result": "REDACTED-TOKEN-STRING"'
  Set-Content "$out\22-token-create-shape.json" (Redact $shape)
  "22-token-create-shape: saved. Revoke 'downbeat-probe-2' in the Music Assistant UI afterwards."
}

# Events: listen for 25 seconds. Press play/pause or change volume on a speaker meanwhile (use any other app).
"Listening for events for 25 seconds. Change a volume or pause/play something now..."
$events = New-Object System.Collections.ArrayList
$deadline = (Get-Date).AddSeconds(25)
$buf = New-Object byte[] 262144
$sb = New-Object Text.StringBuilder
$task = $null
while ((Get-Date) -lt $deadline) {
  if (-not $task) {
    $seg = New-Object ArraySegment[byte] -ArgumentList (,$buf)
    $task = $ws.ReceiveAsync($seg, [Threading.CancellationToken]::None)
  }
  if ($task.Wait(500)) {
    $r = $task.Result; $task = $null
    [void]$sb.Append([Text.Encoding]::UTF8.GetString($buf, 0, $r.Count))
    if ($r.EndOfMessage) { [void]$events.Add($sb.ToString()); [void]$sb.Clear() }
  }
}
$names = @(); foreach ($ev in $events) { try { $names += ($ev | ConvertFrom-Json).event } catch {} }
"Event names seen: " + (($names | Group-Object | ForEach-Object { "$($_.Name) x$($_.Count)" }) -join ', ')
# Save at most the first 2 of each event name (events can be large).
$kept = @{}; $lines = @()
foreach ($ev in $events) { try { $n = ($ev | ConvertFrom-Json).event } catch { $n = '?' }; if ($kept[$n] -lt 2) { $kept[$n]++; $lines += $ev } }
Set-Content "$out\30-events.jsonl" (Redact ($lines -join "`n"))
$ws.Dispose()
"Saved to $out. Tell Claude when done; check the files for anything personal first."
