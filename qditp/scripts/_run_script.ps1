$password = "Passw0rd@_"
$secPass = ConvertTo-SecureString $password -AsPlainText -Force
$cred = New-Object System.Management.Automation.PSCredential("root", $secPass)

Import-Module Posh-SSH -Force
$session = New-SSHSession -ComputerName "172.20.211.21" -Credential $cred -AcceptKey -Force
$sid = $session.SessionId

# 用正斜杠路径
$path = "d:/work/qditp/scripts/_server_script.py"
if (-not (Test-Path $path)) { Write-Error "Not found: $path"; exit 1 }
$scriptBytes = [System.IO.File]::ReadAllBytes($path)
$b64 = [Convert]::ToBase64String($scriptBytes)

$cmd = "echo $b64 | base64 -d | python"
$r = Invoke-SSHCommand -SessionId $sid -Command $cmd
Write-Host "=== Result ==="
Write-Host $r.Output

Remove-SSHSession -SessionId $sid | Out-Null
