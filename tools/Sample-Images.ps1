<#
Read-only check of how Music Assistant describes artwork for library items, and whether image requests need the
token. Run it yourself; Claude never sees your token. Nothing is changed on the server.

  $env:MA_URL = 'https://your-server'        # no trailing slash, kept out of the repo
  .\tools\Sample-Images.ps1                   # prompts for a token (input hidden)

Prints the image objects of a few albums, artists and playlists (addresses redacted), then fetches one image with
and without the token and prints only the HTTP status codes. Output also goes to %TEMP%\downbeat-samples\images.txt
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
    if ($m.message_id -eq $script:id) { return $m }
  }
}
function Redact([string]$t) {
  $t = $t -replace '\b\d{1,3}(\.\d{1,3}){3}\b', 'x.x.x.x'
  $t = $t -replace 'https?://[^/"\s]+', 'https://media.example.com'
  $t
}
$log = New-Object Collections.ArrayList
function Say([string]$s) { $s; [void]$log.Add($s) }

[void](Receive-Msg)                          # server info is sent first
$sec = Read-Host 'Token' -AsSecureString
$token = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($sec))
$a = Send-Cmd 'auth' @{ token = $token }
if (-not $a.result.authenticated) { throw 'Token was not accepted.' }

$firstProxy = $null
foreach ($kind in 'albums', 'artists', 'playlists') {
  $r = Send-Cmd "music/$kind/library_items" @{ limit = 2 }
  Say "== $kind (first 2)"
  foreach ($item in @($r.result)) {
    Say ("name: " + $item.name + "   provider: " + $item.provider + "   uri: " + (Redact $item.uri))
    $imgs = @($item.metadata.images)
    Say ("  metadata.images count: " + $imgs.Count)
    foreach ($img in $imgs | Select-Object -First 3) { Say ("  image: " + (Redact ($img | ConvertTo-Json -Compress))) }
    if ($item.image) { Say ("  item.image: " + (Redact ($item.image | ConvertTo-Json -Compress))) }
    if (-not $firstProxy -and $imgs.Count -gt 0 -and $imgs[0].proxy_id) { $firstProxy = $imgs[0].proxy_id }
  }
}

if ($firstProxy) {
  $url = "$base/imageproxy/$($firstProxy)?size=256&fmt=jpg"
  foreach ($mode in 'without token', 'with Bearer token') {
    try {
      $h = @{}
      if ($mode -eq 'with Bearer token') { $h.Authorization = "Bearer $token" }
      $resp = Invoke-WebRequest -Uri $url -Headers $h -UseBasicParsing -TimeoutSec 30
      Say ("GET imageproxy $mode : HTTP " + $resp.StatusCode + ", " + $resp.RawContentLength + " bytes, " + $resp.Headers['Content-Type'])
    } catch {
      $code = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 'no response' }
      Say ("GET imageproxy $mode : failed, HTTP " + $code)
    }
  }
} else {
  Say 'No proxy_id found on any image, so no image request was tried.'
}
$token = $null
$ws.Dispose()
$out = Join-Path $env:TEMP 'downbeat-samples'
New-Item -ItemType Directory -Force $out | Out-Null
Set-Content "$out\images.txt" ($log -join "`n")
"Saved to $out\images.txt. Paste the output above to Claude (check it for anything personal first)."
