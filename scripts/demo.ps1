param([string]$BaseUrl = 'http://127.0.0.1:8080')
$ErrorActionPreference = 'Stop'
$BaseUrl = $BaseUrl.TrimEnd('/')
function Login-Demo([string]$Email, [string]$Senha) {
    $body = @{email=$Email; senha=$Senha} | ConvertTo-Json
    Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/auth/login" -ContentType 'application/json' -Body $body -TimeoutSec 5
}
$alice = $null
for ($tentativa = 0; $tentativa -lt 30; $tentativa++) {
    try { $alice = Login-Demo 'alice@demo.local' 'DemoAlice123!'; break }
    catch { if ($tentativa -eq 29) { throw 'A demonstração não respondeu. Inicie com o perfil demo e confira a URL.' }; Start-Sleep -Seconds 1 }
}
$bruno = Login-Demo 'bruno@demo.local' 'DemoBruno123!'
$aliceHeaders = @{Authorization='Bearer ' + $alice.token}
$brunoHeaders = @{Authorization='Bearer ' + $bruno.token}
$contasAlice = @(Invoke-RestMethod -Uri "$BaseUrl/api/v1/contas" -Headers $aliceHeaders)
$contasBruno = @(Invoke-RestMethod -Uri "$BaseUrl/api/v1/contas" -Headers $brunoHeaders)
$origem = $contasAlice[0]
$destino = $contasBruno[0]
$saldoAliceAntes = [decimal]$origem.saldo
$saldoBrunoAntes = [decimal]$destino.saldo
$body = @{contaOrigem=$origem.numero; contaDestino=$destino.numero; valor=[decimal]10.00;
          chaveIdempotencia=[Guid]::NewGuid().ToString(); tipoOperacao='PIX'} | ConvertTo-Json
$primeira = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/transferencia" -Headers $aliceHeaders -ContentType 'application/json' -Body $body
$repetida = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/transferencia" -Headers $aliceHeaders -ContentType 'application/json' -Body $body
if ($primeira.id -ne $repetida.id) { throw 'A repetição não devolveu o mesmo comprovante.' }
$saldoAlice = Invoke-RestMethod -Uri "$BaseUrl/api/v1/contas/$($origem.numero)/saldo" -Headers $aliceHeaders
$saldoBruno = Invoke-RestMethod -Uri "$BaseUrl/api/v1/contas/$($destino.numero)/saldo" -Headers $brunoHeaders
if ([decimal]$saldoAlice.saldo -ne ($saldoAliceAntes - 10) -or [decimal]$saldoBruno.saldo -ne ($saldoBrunoAntes + 10)) {
    throw 'Os saldos não correspondem a uma única transferência.'
}
$recusouAcesso = $false
try { Invoke-RestMethod -Uri "$BaseUrl/api/v1/contas/$($destino.numero)/saldo" -Headers $aliceHeaders | Out-Null }
catch { $recusouAcesso = [int]$_.Exception.Response.StatusCode -eq 404 }
if (!$recusouAcesso) { throw 'O acesso à conta de outro titular não foi recusado como esperado.' }
[pscustomobject]@{
    Comprovante=$primeira.id
    SaldoAlice=$saldoAlice.saldo
    SaldoBruno=$saldoBruno.saldo
    RepeticaoSemNovoDebito=($primeira.id -eq $repetida.id)
    ContaDeOutroTitularRecusada=$recusouAcesso
}
