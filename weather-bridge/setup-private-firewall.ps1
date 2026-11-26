# Run this once from an elevated PowerShell window if Windows Firewall blocks the phone.
$ErrorActionPreference = "Stop"
$RuleName = "Artemiz weather relay (private LAN only)"
$ExistingRule = Get-NetFirewallRule -DisplayName $RuleName -ErrorAction SilentlyContinue
if ($null -eq $ExistingRule) {
    New-NetFirewallRule -DisplayName $RuleName `
        -Direction Inbound -Action Allow -Protocol TCP -LocalPort 8765 `
        -Profile Private -RemoteAddress LocalSubnet | Out-Null
    Write-Host "Added a Private-profile-only firewall rule for TCP 8765 from the local subnet."
} else {
    Write-Host "The private LAN weather relay firewall rule already exists."
}
