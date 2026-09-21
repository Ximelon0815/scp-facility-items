# 终端页面全内联: terminal.css + terminal.js 内联进 index.html, 并注入游戏内 Java 桥接隐藏元素
$ErrorActionPreference = "Stop"
$gong = [char]0x5DE5; $cheng = [char]0x7A0B  # 工程
$base = "F:\Desktop\mod" + $gong + $cheng + "\QTE\ui-prototype\terminal"
$htmlPath = Join-Path $base "index.html"
$cssPath = Join-Path $base "terminal.css"
$jsPath = Join-Path $base "terminal.js"
$outPath = Join-Path $base "index.final.html"

$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$html = [System.IO.File]::ReadAllText($htmlPath, [System.Text.Encoding]::UTF8)
$css = [System.IO.File]::ReadAllText($cssPath, [System.Text.Encoding]::UTF8)
$js = [System.IO.File]::ReadAllText($jsPath, [System.Text.Encoding]::UTF8)

# 1. 移除外部 link (terminal.css)
$html = $html -replace '(?i)<link[^>]*href="[^"]*terminal\.css"[^>]*>', ""

# 2. terminal.js 内联进 body (探针后)
$probe = '<script>document.body.setAttribute("data-inline-probe", "1");</script>'
if (-not $html.Contains($probe)) { Write-Output "ERROR probe not found"; exit 1 }
$html = $html.Replace($probe, $probe + "`r`n  <script>`r`n" + $js + "`r`n  </script>")

# 3. 移除外部 script src marker
$marker = '<script src="terminal.js"></script>'
if (-not $html.Contains($marker)) { Write-Output "ERROR marker not found"; exit 1 }
$html = $html.Replace($marker, "")

# 5. 在 </body> 后内联 CSS(终端无 @font-face 外部字体, 可安全内联)
$cssInline = "</body>`r`n<!-- 全内联 CSS(terminal.css) -->`r`n<style>`r`n" + $css + "`r`n</style>"
$html = $html.Replace("</body>", $cssInline)

[System.IO.File]::WriteAllText($outPath, $html, $utf8NoBom)
Write-Output ("OK final written: " + $outPath)
Write-Output ("size = " + $html.Length)
