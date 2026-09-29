# Salva a configuração de envio de e-mail nas variáveis de ambiente do seu usuário do Windows.
# Uso (no PowerShell, dentro da pasta backend):
#   powershell -ExecutionPolicy Bypass -File .\configurar-email.ps1
#
# A senha/chave SMTP é digitada escondida e fica salva só no seu usuário do Windows.

function Perguntar([string]$texto, [string]$padrao) {
    $resposta = Read-Host "$texto [$padrao]"
    if ([string]::IsNullOrWhiteSpace($resposta)) { return $padrao }
    return $resposta.Trim()
}

Write-Host ""
Write-Host "Configuração de e-mail do Descontos Black Friday" -ForegroundColor Yellow
Write-Host "Aperte Enter para aceitar o valor entre colchetes."
Write-Host ""

$servidor = Perguntar "Servidor SMTP (Brevo: smtp-relay.brevo.com / Gmail: smtp.gmail.com)" "smtp-relay.brevo.com"
$porta = Perguntar "Porta" "587"

$login = ""
while ([string]::IsNullOrWhiteSpace($login)) {
    $login = (Read-Host "Login SMTP (Brevo: xxxxxxx@smtp-brevo.com / Gmail: seu e-mail)").Trim()
}

$senha = ""
while ([string]::IsNullOrWhiteSpace($senha)) {
    $segura = Read-Host "Chave SMTP ou senha de app (não aparece enquanto digita)" -AsSecureString
    $senha = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
        [Runtime.InteropServices.Marshal]::SecureStringToBSTR($segura))
}

$padraoRemetente = if ($login -like "*@smtp-brevo.com") { "" } else { $login }
$remetente = ""
while ([string]::IsNullOrWhiteSpace($remetente)) {
    $remetente = Perguntar "E-mail remetente (na Brevo: o e-mail confirmado em Senders)" $padraoRemetente
}

[Environment]::SetEnvironmentVariable("SPRING_MAIL_HOST", $servidor, "User")
[Environment]::SetEnvironmentVariable("SPRING_MAIL_PORT", $porta, "User")
[Environment]::SetEnvironmentVariable("SPRING_MAIL_USERNAME", $login, "User")
[Environment]::SetEnvironmentVariable("SPRING_MAIL_PASSWORD", $senha, "User")
[Environment]::SetEnvironmentVariable("APP_MAIL_REMETENTE", $remetente, "User")
$senha = $null

Write-Host ""
Write-Host "Pronto! Configuração salva:" -ForegroundColor Green
Write-Host "  Servidor:  $servidor"
Write-Host "  Porta:     $porta"
Write-Host "  Login:     $login"
Write-Host "  Senha:     (salva, não exibida)"
Write-Host "  Remetente: $remetente"
Write-Host ""
Write-Host "Reinicie o backend para ele passar a usar essa configuração."
