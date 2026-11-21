$zipPath = 'C:\Users\Saga\Desktop\MCserver\plugins\DamageMessagePlugin\resourcepack.zip'
$port = 8765

if (-not (Test-Path -LiteralPath $zipPath)) {
    throw 'Resource pack ZIP not found.'
}

$listener = New-Object System.Net.HttpListener
$listener.Prefixes.Add("http://+:$port/")
$listener.Start()

Write-Host "Server running at http://26.24.132.217:$port/resourcepack.zip"
Write-Host 'Keep this window open. Press Ctrl+C to stop.'

while ($listener.IsListening) {
    $context = $listener.GetContext()
    $response = $context.Response

    try {
        $path = $context.Request.Url.AbsolutePath
        $method = $context.Request.HttpMethod

        if (($method -eq 'GET') -and ($path -eq '/resourcepack.zip')) {
            $bytes = [System.IO.File]::ReadAllBytes($zipPath)
            $response.StatusCode = 200
            $response.ContentType = 'application/zip'
            $response.ContentLength64 = $bytes.Length
            $response.Headers.Add('Cache-Control', 'no-cache')
            $response.OutputStream.Write($bytes, 0, $bytes.Length)
            Write-Host 'Resource pack sent.'
        }
        else {
            $bytes = [System.Text.Encoding]::ASCII.GetBytes('Not Found')
            $response.StatusCode = 404
            $response.ContentLength64 = $bytes.Length
            $response.OutputStream.Write($bytes, 0, $bytes.Length)
        }
    }
    finally {
        $response.Close()
    }
}