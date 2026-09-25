param([int]$ParentPid = 0)
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)
$OutputEncoding = [Console]::OutputEncoding

Add-Type -AssemblyName System.Runtime.WindowsRuntime
Add-Type -AssemblyName System.Drawing
[Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType=WindowsRuntime] | Out-Null
[Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties, Windows.Media.Control, ContentType=WindowsRuntime] | Out-Null
[Windows.Storage.Streams.IRandomAccessStreamWithContentType, Windows.Storage.Streams, ContentType=WindowsRuntime] | Out-Null

$script:asTaskMethod = [System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
    $_.Name -eq 'AsTask' -and $_.IsGenericMethod -and $_.GetParameters().Count -eq 1
} | Select-Object -First 1
$script:asStreamForReadMethod = [System.IO.WindowsRuntimeStreamExtensions].GetMethods() | Where-Object {
    $_.Name -eq 'AsStreamForRead' -and $_.GetParameters().Count -eq 1
} | Select-Object -First 1

function Await-Result($operation, [Type]$resultType) {
    $task = $script:asTaskMethod.MakeGenericMethod($resultType).Invoke($null, @($operation))
    $task.Wait()
    return $task.Result
}

function Read-Thumbnail($thumbnail) {
    if ($null -eq $thumbnail) { return $null }
    $randomAccess = Await-Result ($thumbnail.OpenReadAsync()) ([Windows.Storage.Streams.IRandomAccessStreamWithContentType])
    if ($null -eq $randomAccess) { return $null }
    $stream = $script:asStreamForReadMethod.Invoke($null, @($randomAccess))
    try {
        $img = [System.Drawing.Image]::FromStream($stream)
        try {
            $bmp = New-Object System.Drawing.Bitmap($img, 64, 64)
            try {
                $out = New-Object System.IO.MemoryStream
                try {
                    $bmp.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)
                    return [Convert]::ToBase64String($out.ToArray())
                } finally { $out.Dispose() }
            } finally { $bmp.Dispose() }
        } finally { $img.Dispose() }
    } finally { $stream.Dispose() }
}

$manager = Await-Result ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
$propertiesType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties]
$lastKey = ''

while ($true) {
    if ($ParentPid -gt 0 -and -not (Get-Process -Id $ParentPid -ErrorAction SilentlyContinue)) { exit }
    try {
        $session = $manager.GetCurrentSession()
        if ($null -eq $session) {
            [pscustomobject]@{ available = $false } | ConvertTo-Json -Compress
            $lastKey = ''
        } else {
            $properties = Await-Result ($session.TryGetMediaPropertiesAsync()) $propertiesType
            $timeline = $session.GetTimelineProperties()
            $playback = $session.GetPlaybackInfo()
            $key = "$($session.SourceAppUserModelId)|$($properties.Title)|$($properties.Artist)|$($properties.AlbumTitle)"
            $artwork = $null
            if ($key -ne $lastKey) {
                try { $artwork = Read-Thumbnail $properties.Thumbnail } catch { $artwork = $null }
                $lastKey = $key
            }
            [pscustomobject]@{
                available = $true
                source = $session.SourceAppUserModelId
                title = $properties.Title
                artist = $properties.Artist
                album = $properties.AlbumTitle
                positionMs = [long]$timeline.Position.TotalMilliseconds
                durationMs = [long]$timeline.EndTime.TotalMilliseconds
                playing = ($playback.PlaybackStatus.ToString() -eq 'Playing')
                key = $key
                artwork = $artwork
            } | ConvertTo-Json -Compress
        }
    } catch {
        [pscustomobject]@{ available = $false } | ConvertTo-Json -Compress
    }
    Start-Sleep -Milliseconds 1000
}
