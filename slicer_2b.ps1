$inputFile = "C:\Users\imnot\AndroidStudioProjects\PoLitAI_Phi3\app\src\main\assets\gemma-3n-E2B-it-int4.task"
$outputPrefix = "C:\Users\imnot\AndroidStudioProjects\PoLitAI_Phi3\app\src\main\assets\gemma_3n_2b"
$chunkSize = 100 * 1024 * 1024 # 100MB chunks

if (!(Test-Path $inputFile)) {
    Write-Error "Input file not found: $inputFile"
    exit 1
}

$fileStream = [System.IO.File]::OpenRead($inputFile)
$buffer = New-Object byte[] $chunkSize
$chunkNum = 1

try {
    while ($true) {
        $bytesRead = $fileStream.Read($buffer, 0, $chunkSize)
        if ($bytesRead -le 0) { break }
        
        $outputFile = "$outputPrefix.part$chunkNum"
        Write-Host "Writing $outputFile... ($([math]::Round($bytesRead / 1MB, 2)) MB)"
        $outputStream = [System.IO.File]::OpenWrite($outputFile)
        $outputStream.Write($buffer, 0, $bytesRead)
        $outputStream.Close()
        
        $chunkNum++
    }
    $fileStream.Close()
    Write-Host "Slicing complete! Total chunks: $($chunkNum - 1)"
    
    # Delete original to prevent array size crash in Gradle
    Remove-Item $inputFile -Force
    Write-Host "Deleted original massive file: $inputFile"
}
finally {
    $fileStream.Dispose()
}
