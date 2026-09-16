$password = "Passw0rd@_"
$secPass = ConvertTo-SecureString $password -AsPlainText -Force
$cred = New-Object System.Management.Automation.PSCredential("root", $secPass)

Import-Module Posh-SSH -Force
$session = New-SSHSession -ComputerName "172.20.211.21" -Credential $cred -AcceptKey -Force
$sid = $session.SessionId

# 把 Python 代码 base64 编码，避免所有引号问题
$pyCode = @'
import os
key1 = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIEwaqJp/QsPHRxCPxeqfCooi8hLkIogqo62sIK/+aLdB k8s-master"
key2 = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIASueeOO2357yHqsyMW2zXSpRqqXgIVsmmNBnInwTyil yaki@LAPTOP-F2CQKD06"
with open("/root/.ssh/authorized_keys", "w") as f:
    f.write(key1 + chr(10))
    f.write(key2 + chr(10))
os.chmod("/root/.ssh/authorized_keys", 0o600)
os.chmod("/root/.ssh", 0o700)
print("done")
'@

$pyB64 = [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($pyCode))
$cmd = "python -c `"import base64; exec(base64.b64decode('$pyB64'))`""
$r = Invoke-SSHCommand -SessionId $sid -Command $cmd
Write-Host "=== Result ==="
Write-Host $r.Output

Remove-SSHSession -SessionId $sid | Out-Null
