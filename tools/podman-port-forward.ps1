[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [ValidateSet('Start', 'Stop', 'Restart', 'Status', 'Test')]
    [string] $Action = 'Status',

    [string] $MachineName = 'podman-machine-default',
    [string] $StatePath,
    [int] $ConsolePort = 8080,
    [int] $OperationsPort = 8084,
    [int] $MqttPort = 1883,
    [int] $KafkaPort = 9092,
    [int] $TimescalePort = 15432,
    [string] $TimescaleUser = 'watermonitor',
    [string] $TimescaleDatabase = 'watermonitor',
    [string] $TimescalePassword = 'watermonitor-dev-only',
    [switch] $NoCheck,
    [switch] $Force,
    [switch] $StrictPorts
)

Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($StatePath)) {
    $stateRoot = if (-not [string]::IsNullOrWhiteSpace($env:LOCALAPPDATA)) {
        Join-Path $env:LOCALAPPDATA 'water-monitoring-system-iot-esp32'
    } else {
        Join-Path $env:TEMP 'water-monitoring-system-iot-esp32'
    }
    $StatePath = Join-Path $stateRoot 'podman-port-forward.json'
}

function Write-Info([string] $Message) {
    Write-Host "[podman-forward] $Message"
}

function Fail([string] $Message) {
    throw "[podman-forward] $Message"
}

function Invoke-Podman([string[]] $Arguments) {
    $output = @(& podman @Arguments 2>&1)
    if ($LASTEXITCODE -ne 0) {
        Fail "podman $($Arguments -join ' ') failed:`n$($output -join [Environment]::NewLine)"
    }
    return $output
}

function Get-Machine() {
    $raw = Invoke-Podman @('machine', 'inspect', $MachineName)
    try {
        $machine = @($raw -join [Environment]::NewLine | ConvertFrom-Json)[0]
    } catch {
        Fail "Could not parse podman machine inspect output: $($_.Exception.Message)"
    }
    if ($null -eq $machine) {
        Fail "Podman machine '$MachineName' was not found."
    }
    if ([string]$machine.State -ne 'running') {
        Fail "Podman machine '$MachineName' is not running (state: $($machine.State)). Start it with: podman machine start $MachineName"
    }
    if ($null -eq $machine.SSHConfig -or [int]$machine.SSHConfig.Port -le 0) {
        Fail "Podman machine '$MachineName' did not report an SSH port."
    }
    $key = [string]$machine.SSHConfig.IdentityPath
    if (-not (Test-Path -LiteralPath $key -PathType Leaf)) {
        Fail "Podman machine SSH identity was not found: $key"
    }
    [pscustomobject]@{
        Name = $MachineName
        Host = '127.0.0.1'
        Port = [int]$machine.SSHConfig.Port
        User = [string]$machine.SSHConfig.RemoteUsername
        IdentityPath = (Resolve-Path -LiteralPath $key).Path
    }
}

function Get-ContainerName([string] $Service) {
    $names = @(Invoke-Podman @('ps', '--format', '{{.Names}}')) |
        Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
    $pattern = '(^|[-_])' + [regex]::Escape($Service) + '([-_]|$)'
    $matches = @($names | Where-Object { $_ -match $pattern })
    if ($matches.Count -eq 0) {
        Fail "No running Podman container matched service '$Service'. Running containers: $($names -join ', ')"
    }
    if ($matches.Count -gt 1) {
        $preferred = $matches | Where-Object { $_ -eq "wtm-$Service" -or $_ -eq "watermonitor-dev-$Service-1" }
        if ($null -ne $preferred) {
            return [string](@($preferred)[0])
        }
    }
    return [string]$matches[0]
}

function Get-ContainerIp([string] $ContainerName) {
    $raw = Invoke-Podman @('inspect', $ContainerName)
    try {
        $container = @($raw -join [Environment]::NewLine | ConvertFrom-Json)[0]
    } catch {
        Fail "Could not parse podman inspect output for '$ContainerName': $($_.Exception.Message)"
    }
    $networkProperties = @($container.NetworkSettings.Networks.PSObject.Properties)
    $network = $networkProperties |
        Where-Object { $_.Name -eq 'watermonitor-dev_default' } |
        Select-Object -First 1
    if ($null -eq $network) {
        $network = $networkProperties | Select-Object -First 1
    }
    $ip = if ($null -ne $network) { [string]$network.Value.IPAddress } else { '' }
    if ($ip -notmatch '^\d{1,3}(\.\d{1,3}){3}$') {
        Fail "Container '$ContainerName' did not report an IPv4 address on its network."
    }
    return $ip
}

function Get-Discovery([object] $Machine) {
    $definitions = @(
        [pscustomobject]@{ Name = 'console'; Service = 'console'; LocalPort = $ConsolePort; RemotePort = 8080 },
        [pscustomobject]@{ Name = 'operations-api'; Service = 'operations-api'; LocalPort = $OperationsPort; RemotePort = 8084 },
        [pscustomobject]@{ Name = 'mosquitto'; Service = 'mosquitto'; LocalPort = $MqttPort; RemotePort = 1883 },
        [pscustomobject]@{ Name = 'kafka'; Service = 'kafka'; LocalPort = $KafkaPort; RemotePort = 9092 },
        [pscustomobject]@{ Name = 'timescaledb'; Service = 'timescaledb'; LocalPort = $TimescalePort; RemotePort = 5432 }
    )
    $forwards = foreach ($definition in $definitions) {
        $container = Get-ContainerName $definition.Service
        [pscustomobject]@{
            Name = $definition.Name
            Container = $container
            RemoteAddress = Get-ContainerIp $container
            RemotePort = [int]$definition.RemotePort
            LocalAddress = '127.0.0.1'
            LocalPort = [int]$definition.LocalPort
        }
    }
    [pscustomobject]@{
        Machine = $Machine
        Forwards = @($forwards)
    }
}

function Read-State() {
    if (-not (Test-Path -LiteralPath $StatePath -PathType Leaf)) {
        return $null
    }
    try {
        return Get-Content -LiteralPath $StatePath -Raw | ConvertFrom-Json
    } catch {
        if ($Force) {
            Write-Info "Ignoring malformed state because -Force was supplied: $StatePath"
            Remove-Item -LiteralPath $StatePath -Force -ErrorAction SilentlyContinue
            return $null
        }
        Fail "State file '$StatePath' is not valid JSON. Remove it manually or use Stop -Force: $($_.Exception.Message)"
    }
}

function Write-State([object] $State) {
    $directory = Split-Path -Parent $StatePath
    if (-not (Test-Path -LiteralPath $directory)) {
        New-Item -ItemType Directory -Path $directory -Force | Out-Null
    }
    $temporary = "$StatePath.$PID.tmp"
    $State | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $temporary -Encoding UTF8
    Move-Item -LiteralPath $temporary -Destination $StatePath -Force
}

function Remove-State() {
    if (Test-Path -LiteralPath $StatePath -PathType Leaf) {
        Remove-Item -LiteralPath $StatePath -Force
    }
}

function Get-ProcessCommandLine([int] $ProcessId) {
    try {
        $process = Get-CimInstance Win32_Process -Filter "ProcessId=$ProcessId"
        if ($null -ne $process) {
            return $process
        }
    } catch {
        # Get-CimInstance is unavailable on a few constrained PowerShell hosts;
        # process-name validation below still protects Stop from killing an
        # arbitrary PID.
    }
    return $null
}

function Test-SshTunnelProcess([int] $ProcessId, [object] $Machine, [object[]] $Forwards) {
    $process = Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
    if ($null -eq $process -or $process.ProcessName -notmatch '^ssh$') {
        return $false
    }
    $details = Get-ProcessCommandLine $ProcessId
    if ($null -eq $details -or [string]::IsNullOrWhiteSpace([string]$details.CommandLine)) {
        return $false
    }
    $command = [string]$details.CommandLine
    if ($command -notmatch ('-p\s+' + [regex]::Escape([string]$Machine.Port))) {
        return $false
    }
    foreach ($forward in $Forwards) {
        if ($command -notmatch ([regex]::Escape("127.0.0.1:$($forward.LocalPort):$($forward.RemoteAddress):$($forward.RemotePort)"))) {
            return $false
        }
    }
    return $true
}

function Test-SshTunnelLocalPorts([int] $ProcessId, [object] $Machine, [object[]] $Forwards) {
    $process = Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
    if ($null -eq $process -or $process.ProcessName -notmatch '^ssh$') {
        return $false
    }
    $details = Get-ProcessCommandLine $ProcessId
    if ($null -eq $details -or [string]::IsNullOrWhiteSpace([string]$details.CommandLine)) {
        return $false
    }
    $command = [string]$details.CommandLine
    if ($command -notmatch ('-p\s+' + [regex]::Escape([string]$Machine.Port))) {
        return $false
    }
    foreach ($forward in $Forwards) {
        if ($command -notmatch ([regex]::Escape("127.0.0.1:$($forward.LocalPort):"))) {
            return $false
        }
    }
    return $true
}

function Get-MatchingSshProcesses([object] $Machine, [object[]] $Forwards) {
    $result = @()
    try {
        $processes = Get-CimInstance Win32_Process | Where-Object { $_.Name -match '^ssh(\.exe)?$' }
        foreach ($process in $processes) {
            $command = [string]$process.CommandLine
            if ($command -notmatch ('-p\s+' + [regex]::Escape([string]$Machine.Port))) {
                continue
            }
            $allMatch = $true
            foreach ($forward in $Forwards) {
                if ($command -notmatch ([regex]::Escape("127.0.0.1:$($forward.LocalPort):$($forward.RemoteAddress):$($forward.RemotePort)"))) {
                    $allMatch = $false
                    break
                }
            }
            if ($allMatch) {
                $result += $process
            }
        }
    } catch {
        # A missing CIM provider only disables stale-process discovery.
    }
    return @($result)
}

function Stop-OwnedProcess([int] $ProcessId, [object] $Machine, [object[]] $Forwards) {
    if (-not (Test-SshTunnelProcess $ProcessId $Machine $Forwards)) {
        if ($Force) {
            Write-Info "Recorded PID $ProcessId is not a matching SSH tunnel; -Force removes state without stopping it."
            return
        }
        Fail "Refusing to stop PID $ProcessId because it is not the recorded Podman SSH tunnel."
    }
    Stop-Process -Id $ProcessId -Force -ErrorAction SilentlyContinue
    Start-Sleep -Milliseconds 250
}

function Get-ListeningOwners([int[]] $Ports) {
    $owners = @()
    try {
        $owners = @(Get-NetTCPConnection -State Listen -ErrorAction Stop |
            Where-Object { $Ports -contains [int]$_.LocalPort } |
            Select-Object LocalAddress, LocalPort, OwningProcess)
    } catch {
        # Older Windows PowerShell may lack NetTCPIP; an occupied-port error
        # will be caught by Start-Process/ExitOnForwardFailure as a fallback.
    }
    return $owners
}

function Assert-PortsFree([object[]] $Forwards, [object] $Machine) {
    $ports = @($Forwards | ForEach-Object { [int]$_.LocalPort })
    $owners = Get-ListeningOwners $ports
    foreach ($owner in $owners) {
        $process = Get-Process -Id ([int]$owner.OwningProcess) -ErrorAction SilentlyContinue
        if ($null -ne $process -and $process.ProcessName -match '^ssh$') {
            $details = Get-ProcessCommandLine ([int]$owner.OwningProcess)
            $command = if ($null -ne $details) { [string]$details.CommandLine } else { '' }
            if (Test-SshTunnelLocalPorts ([int]$owner.OwningProcess) $Machine $Forwards) {
                Stop-Process -Id ([int]$owner.OwningProcess) -Force -ErrorAction SilentlyContinue
                continue
            }
        }
        $name = if ($null -ne $process) { $process.ProcessName } else { 'unknown' }
        Fail "Local port $($owner.LocalPort) is already owned by PID $($owner.OwningProcess) ($name). Podman published ports are still active or another application is using this port. Remove the published mapping/recreate that container without -p, then retry. No unrelated process was stopped."
    }
}

function Resolve-ForwardPorts([object] $Discovery) {
    if ($StrictPorts) {
        return $Discovery
    }

    # Podman's Windows VirtioProxy can leave a dead listener behind. Keep the
    # normal ports whenever possible, but make the zero-argument workflow
    # usable by selecting stable, documented fallbacks for HTTP, MQTT, and Kafka.
    $state = Read-State
    $ownedPid = if ($null -ne $state) { [int]$state.Pid } else { -1 }
    $resolved = foreach ($forward in @($Discovery.Forwards)) {
        $localPort = [int]$forward.LocalPort
        $fallback = 0
        if ($forward.Name -eq 'console' -and $localPort -eq 8080) { $fallback = 18080 }
        if ($forward.Name -eq 'operations-api' -and $localPort -eq 8084) { $fallback = 18084 }
        if ($forward.Name -eq 'mosquitto' -and $localPort -eq 1883) { $fallback = 11883 }
        if ($forward.Name -eq 'kafka' -and $localPort -eq 9092) { $fallback = 19092 }

        if ($fallback -gt 0) {
            $occupied = @(Get-ListeningOwners @($localPort) |
                Where-Object { [int]$_.OwningProcess -ne $ownedPid })
            if ($occupied.Count -gt 0) {
                $fallbackOccupied = @(Get-ListeningOwners @($fallback) |
                    Where-Object { [int]$_.OwningProcess -ne $ownedPid })
                if ($fallbackOccupied.Count -gt 0) {
                    Fail "Local port $localPort is occupied and its fallback $fallback is also occupied. Use -StrictPorts to demand exact ports after removing the published mapping, or choose a free port explicitly."
                }
                Write-Info "Local port $localPort is occupied by an existing host listener; using fallback $fallback."
                $localPort = $fallback
            }
        }

        [pscustomobject]@{
            Name = $forward.Name
            Container = $forward.Container
            RemoteAddress = $forward.RemoteAddress
            RemotePort = [int]$forward.RemotePort
            LocalAddress = $forward.LocalAddress
            LocalPort = $localPort
        }
    }
    return [pscustomobject]@{
        Machine = $Discovery.Machine
        Forwards = @($resolved)
    }
}

function Quote-ProcessArgument([string] $Value) {
    if ($Value -match '[\s"]') {
        return '"' + $Value.Replace('"', '\"') + '"'
    }
    return $Value
}

function Get-SshArguments([object] $Machine, [object[]] $Forwards) {
    $arguments = @(
        '-i', $Machine.IdentityPath,
        '-p', [string]$Machine.Port,
        '-o', 'BatchMode=yes',
        '-o', 'ExitOnForwardFailure=yes',
        '-o', 'ServerAliveInterval=15',
        '-o', 'ServerAliveCountMax=3',
        '-o', 'StrictHostKeyChecking=no',
        '-o', 'UserKnownHostsFile=NUL',
        '-N',
        '-T'
    )
    foreach ($forward in $Forwards) {
        $arguments += @('-L', "127.0.0.1:$($forward.LocalPort):$($forward.RemoteAddress):$($forward.RemotePort)")
    }
    $arguments += "$($Machine.User)@$($Machine.Host)"
    return @($arguments)
}

function Get-LogPath() {
    $logDirectory = Join-Path (Split-Path -Parent $StatePath) 'logs'
    if (-not (Test-Path -LiteralPath $logDirectory)) {
        New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null
    }
    return Join-Path $logDirectory 'podman-port-forward.log'
}

function Get-ErrorLogPath() {
    $logDirectory = Join-Path (Split-Path -Parent $StatePath) 'logs'
    if (-not (Test-Path -LiteralPath $logDirectory)) {
        New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null
    }
    return Join-Path $logDirectory 'podman-port-forward.err.log'
}

function Start-Tunnel([object] $Discovery) {
    $state = Read-State
    if ($null -ne $state) {
        $existingForwards = @($state.Forwards)
        $isAlive = Test-SshTunnelProcess ([int]$state.Pid) $state.Machine $existingForwards
        if ($isAlive) {
            $same = (($existingForwards | ConvertTo-Json -Compress) -eq ($Discovery.Forwards | ConvertTo-Json -Compress)) -and
                ([string]$state.Machine.Name -eq [string]$Discovery.Machine.Name) -and
                ([int]$state.Machine.Port -eq [int]$Discovery.Machine.Port)
            if ($same) {
                Write-Info "Tunnel already running (PID $($state.Pid)); current container IPs match."
                if (-not $NoCheck) { Invoke-ForwardChecks $Discovery.Forwards }
                return
            }
            Write-Info 'Container IPs or machine SSH endpoint changed; stopping the stale recorded tunnel.'
            Stop-OwnedProcess ([int]$state.Pid) $state.Machine $existingForwards
        }
        Remove-State
    }

    $matching = @(Get-MatchingSshProcesses $Discovery.Machine $Discovery.Forwards)
    foreach ($process in $matching) {
        Write-Info "Stopping stale matching SSH tunnel PID $($process.ProcessId)."
        Stop-Process -Id ([int]$process.ProcessId) -Force -ErrorAction SilentlyContinue
    }
    Assert-PortsFree $Discovery.Forwards $Discovery.Machine

    $ssh = Get-Command ssh.exe -ErrorAction SilentlyContinue
    if ($null -eq $ssh) { Fail 'OpenSSH ssh.exe was not found on PATH.' }
    $log = Get-LogPath
    $args = Get-SshArguments $Discovery.Machine $Discovery.Forwards
    $argString = ($args | ForEach-Object { Quote-ProcessArgument ([string]$_) }) -join ' '
    Write-Info "Starting SSH local forwards through $($Discovery.Machine.Name) ($($Discovery.Machine.Host):$($Discovery.Machine.Port))."
    $errorLog = Get-ErrorLogPath
    $process = Start-Process -FilePath $ssh.Source -ArgumentList $argString -RedirectStandardOutput $log -RedirectStandardError $errorLog -PassThru -WindowStyle Hidden
    Start-Sleep -Milliseconds 750
    if ($process.HasExited) {
        $details = if (Test-Path -LiteralPath $log) { Get-Content -LiteralPath $log -Raw } else { '' }
        $errors = if (Test-Path -LiteralPath $errorLog) { Get-Content -LiteralPath $errorLog -Raw } else { '' }
        Fail "SSH tunnel exited immediately (code $($process.ExitCode)). Logs: $log and $errorLog`n$details`n$errors"
    }

    $state = [ordered]@{
        Version = 1
        Pid = [int]$process.Id
        StartedAt = (Get-Date).ToUniversalTime().ToString('o')
        LogPath = $log
        Machine = $Discovery.Machine
        Forwards = $Discovery.Forwards
    }
    Write-State $state
    if (-not $NoCheck) {
        try {
            Invoke-ForwardChecks $Discovery.Forwards
        } catch {
            Stop-OwnedProcess ([int]$process.Id) $Discovery.Machine $Discovery.Forwards
            Remove-State
            throw
        }
    }
    Write-Info "Started tunnel PID $($process.Id). State: $StatePath"
}

function Stop-Tunnel() {
    $state = Read-State
    if ($null -eq $state) {
        Write-Info "No recorded tunnel state at $StatePath."
        return
    }
    $forwards = @($state.Forwards)
    if (Test-SshTunnelProcess ([int]$state.Pid) $state.Machine $forwards) {
        Stop-OwnedProcess ([int]$state.Pid) $state.Machine $forwards
        Write-Info "Stopped tunnel PID $($state.Pid)."
    } else {
        Write-Info "Recorded tunnel PID $($state.Pid) is not running or no longer matches; no process was stopped."
    }
    Remove-State
}

function Get-TcpConnection([int] $Port, [int] $TimeoutMs = 2500) {
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $async = $client.BeginConnect('127.0.0.1', $Port, $null, $null)
        if (-not $async.AsyncWaitHandle.WaitOne($TimeoutMs)) {
            return $null
        }
        $client.EndConnect($async)
        $client.ReceiveTimeout = $TimeoutMs
        $client.SendTimeout = $TimeoutMs
        return $client
    } catch {
        $client.Dispose()
        return $null
    }
}

function Add-Be16([System.Collections.Generic.List[byte]] $Bytes, [int] $Value) {
    $Bytes.Add([byte](($Value -shr 8) -band 0xff)); $Bytes.Add([byte]($Value -band 0xff))
}

function Add-Be32([System.Collections.Generic.List[byte]] $Bytes, [int] $Value) {
    $Bytes.Add([byte](($Value -shr 24) -band 0xff)); $Bytes.Add([byte](($Value -shr 16) -band 0xff));
    $Bytes.Add([byte](($Value -shr 8) -band 0xff)); $Bytes.Add([byte]($Value -band 0xff))
}

function Get-Be32([byte[]] $Bytes, [int] $Offset = 0) {
    return (([int]$Bytes[$Offset] -shl 24) -bor ([int]$Bytes[$Offset + 1] -shl 16) -bor ([int]$Bytes[$Offset + 2] -shl 8) -bor [int]$Bytes[$Offset + 3])
}

function Read-Exact([System.Net.Sockets.NetworkStream] $Stream, [int] $Count) {
    $buffer = New-Object byte[] $Count
    $read = 0
    while ($read -lt $Count) {
        $chunk = $Stream.Read($buffer, $read, $Count - $read)
        if ($chunk -le 0) { throw 'Remote endpoint closed the connection.' }
        $read += $chunk
    }
    return $buffer
}

function Write-Packet([System.Net.Sockets.NetworkStream] $Stream, [byte[]] $Payload) {
    $Stream.Write($Payload, 0, $Payload.Length)
    $Stream.Flush()
}

function Test-Mqtt([int] $Port) {
    $client = Get-TcpConnection $Port
    if ($null -eq $client) { return $false }
    try {
        $bytes = New-Object 'System.Collections.Generic.List[byte]'
        $clientId = [Text.Encoding]::ASCII.GetBytes('podman-forward-check')
        $variable = New-Object 'System.Collections.Generic.List[byte]'
        $variable.Add(0); $variable.Add(4); $variable.AddRange([Text.Encoding]::ASCII.GetBytes('MQTT')); $variable.Add(4); $variable.Add(2); Add-Be16 $variable 60
        Add-Be16 $variable $clientId.Length; $variable.AddRange($clientId)
        $remaining = $variable.Count
        $bytes.Add(0x10); $bytes.Add([byte]$remaining); $bytes.AddRange($variable)
        Write-Packet $client.GetStream() $bytes.ToArray()
        $response = Read-Exact $client.GetStream() 4
        return ($response[0] -eq 0x20 -and $response[3] -eq 0)
    } catch { return $false } finally { $client.Dispose() }
}

function Test-Kafka([int] $Port) {
    $client = Get-TcpConnection $Port
    if ($null -eq $client) { return $false }
    try {
        $payload = New-Object 'System.Collections.Generic.List[byte]'
        Add-Be16 $payload 18; Add-Be16 $payload 0; Add-Be32 $payload 1
        $clientId = [Text.Encoding]::ASCII.GetBytes('podman-forward')
        Add-Be16 $payload $clientId.Length; $payload.AddRange($clientId)
        $request = New-Object 'System.Collections.Generic.List[byte]'
        Add-Be32 $request $payload.Count; $request.AddRange($payload)
        Write-Packet $client.GetStream() $request.ToArray()
        $length = Get-Be32 (Read-Exact $client.GetStream() 4)
        return ($length -gt 0 -and $length -lt 1048576)
    } catch { return $false } finally { $client.Dispose() }
}

function Get-ScramAttribute([string] $Message, [string] $Name) {
    foreach ($part in $Message.Split(',')) {
        if ($part.StartsWith("$Name=")) { return $part.Substring($Name.Length + 1) }
    }
    return $null
}

function Get-HmacSha256([byte[]] $Key, [string] $Text) {
    $hmac = New-Object System.Security.Cryptography.HMACSHA256 (,$Key)
    try { return $hmac.ComputeHash([Text.Encoding]::UTF8.GetBytes($Text)) } finally { $hmac.Dispose() }
}

function Get-Sha256([byte[]] $Bytes) {
    $hash = [Security.Cryptography.SHA256]::Create()
    try { return $hash.ComputeHash($Bytes) } finally { $hash.Dispose() }
}

function Xor-Bytes([byte[]] $Left, [byte[]] $Right) {
    $result = New-Object byte[] $Left.Length
    for ($i = 0; $i -lt $Left.Length; $i++) { $result[$i] = $Left[$i] -bxor $Right[$i] }
    return $result
}

function Read-PgMessage([System.Net.Sockets.NetworkStream] $Stream) {
    $type = [Text.Encoding]::ASCII.GetString((Read-Exact $Stream 1))
    $length = Get-Be32 (Read-Exact $Stream 4)
    if ($length -lt 4 -or $length -gt 16777216) { throw "Invalid PostgreSQL message length: $length" }
    [pscustomobject]@{ Type = $type; Payload = (Read-Exact $Stream ($length - 4)) }
}

function Write-PgMessage([System.Net.Sockets.NetworkStream] $Stream, [string] $Type, [byte[]] $Payload) {
    $message = New-Object 'System.Collections.Generic.List[byte]'
    $message.Add([Text.Encoding]::ASCII.GetBytes($Type)[0]); Add-Be32 $message ($Payload.Length + 4); $message.AddRange($Payload)
    Write-Packet $Stream $message.ToArray()
}

function New-PgStartup([string] $User, [string] $Database) {
    $body = New-Object 'System.Collections.Generic.List[byte]'
    Add-Be32 $body 196608
    $body.AddRange([Text.Encoding]::UTF8.GetBytes("user`0$User`0database`0$Database`0`0"))
    $message = New-Object 'System.Collections.Generic.List[byte]'; Add-Be32 $message ($body.Count + 4); $message.AddRange($body)
    return $message.ToArray()
}

function Test-Postgres([int] $Port, [string] $User, [string] $Database, [string] $Password) {
    $client = Get-TcpConnection $Port
    if ($null -eq $client) { return $false }
    try {
        $stream = $client.GetStream(); Write-Packet $stream (New-PgStartup $User $Database)
        $message = Read-PgMessage $stream
        if ($message.Type -eq 'E') { throw 'PostgreSQL returned an error during startup.' }
        if ($message.Type -ne 'R') { throw "Unexpected PostgreSQL startup message: $($message.Type)" }
        $authCode = Get-Be32 $message.Payload
        if ($authCode -eq 0) {
            # Trust authentication is unusual but valid in a development DB.
        } elseif ($authCode -eq 10) {
            $nonce = [Guid]::NewGuid().ToString('N')
            $escapedUser = $User.Replace('=', '=3D').Replace(',', '=2C')
            $clientFirstBare = "n=$escapedUser,r=$nonce"
            $initial = [Text.Encoding]::UTF8.GetBytes("n,,$clientFirstBare")
            $sasl = New-Object 'System.Collections.Generic.List[byte]'; $sasl.AddRange([Text.Encoding]::ASCII.GetBytes("SCRAM-SHA-256`0")); Add-Be32 $sasl $initial.Length; $sasl.AddRange($initial)
            # PasswordMessage payload for SASL starts with the mechanism name;
            # the AuthenticationSASL request's numeric code is server-only.
            Write-PgMessage $stream 'p' $sasl.ToArray()
            $message = Read-PgMessage $stream
            if ($message.Type -ne 'R' -or (Get-Be32 $message.Payload) -ne 11) { throw 'PostgreSQL did not return SCRAM-SHA-256 continue.' }
            $serverFirst = [Text.Encoding]::UTF8.GetString($message.Payload, 4, $message.Payload.Length - 4)
            $serverNonce = Get-ScramAttribute $serverFirst 'r'; $saltText = Get-ScramAttribute $serverFirst 's'; $iterations = [int](Get-ScramAttribute $serverFirst 'i')
            if ([string]::IsNullOrWhiteSpace($serverNonce) -or [string]::IsNullOrWhiteSpace($saltText) -or $iterations -le 0) { throw 'Malformed PostgreSQL SCRAM challenge.' }
            $salted = New-Object System.Security.Cryptography.Rfc2898DeriveBytes($Password, [Convert]::FromBase64String($saltText), $iterations, [Security.Cryptography.HashAlgorithmName]::SHA256)
            try { $saltedPassword = $salted.GetBytes(32) } finally { $salted.Dispose() }
            $clientFinalWithoutProof = "c=biws,r=$serverNonce"; $authMessage = "$clientFirstBare,$serverFirst,$clientFinalWithoutProof"
            $clientKey = Get-HmacSha256 $saltedPassword 'Client Key'; $storedKey = Get-Sha256 $clientKey; $signature = Get-HmacSha256 $storedKey $authMessage
            $proof = [Convert]::ToBase64String((Xor-Bytes $clientKey $signature)); $final = [Text.Encoding]::UTF8.GetBytes("$clientFinalWithoutProof,p=$proof")
            Write-PgMessage $stream 'p' $final
            $message = Read-PgMessage $stream
            if ($message.Type -ne 'R' -or (Get-Be32 $message.Payload) -ne 12) { throw 'PostgreSQL SCRAM authentication failed.' }
            $message = Read-PgMessage $stream
        } elseif ($authCode -eq 3) {
            $passwordBytes = [Text.Encoding]::UTF8.GetBytes($Password); $passwordPayload = New-Object 'System.Collections.Generic.List[byte]'; $passwordPayload.AddRange($passwordBytes); $passwordPayload.Add(0); Write-PgMessage $stream 'p' $passwordPayload.ToArray(); $message = Read-PgMessage $stream
        } else {
            throw "Unsupported PostgreSQL authentication method: $authCode"
        }
        while ($message.Type -ne 'Z') {
            if ($message.Type -eq 'E') { throw 'PostgreSQL authentication failed.' }
            $message = Read-PgMessage $stream
        }
        $query = [Text.Encoding]::UTF8.GetBytes("SELECT 1 AS reachable`0")
        $queryPayload = New-Object 'System.Collections.Generic.List[byte]'; $queryPayload.AddRange($query); Write-PgMessage $stream 'Q' $queryPayload.ToArray()
        $value = $null
        do {
            $message = Read-PgMessage $stream
            if ($message.Type -eq 'E') { return $false }
            if ($message.Type -eq 'D') {
                $fieldCount = ($message.Payload[0] -shl 8) -bor $message.Payload[1]; $offset = 2
                if ($fieldCount -gt 0) { $length = Get-Be32 $message.Payload $offset; $offset += 4; if ($length -ge 0) { $value = [Text.Encoding]::UTF8.GetString($message.Payload, $offset, $length) } }
            }
        } while ($message.Type -ne 'Z')
        return ($value -eq '1')
    } catch { Write-Verbose "PostgreSQL check failed: $($_.Exception.Message)"; return $false } finally { $client.Dispose() }
}

function Invoke-ForwardChecks([object[]] $Forwards) {
    $results = @()
    foreach ($forward in $Forwards) {
        $tcp = Get-TcpConnection ([int]$forward.LocalPort)
        $tcpOk = $null -ne $tcp
        if ($null -ne $tcp) { $tcp.Dispose() }
        $detail = "TCP $($forward.LocalAddress):$($forward.LocalPort) -> $($forward.RemoteAddress):$($forward.RemotePort)"
        if ($forward.Name -eq 'console') {
            try { $response = Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:$($forward.LocalPort)/healthz" -TimeoutSec 5; $ok = ($response.StatusCode -eq 200); $detail += "; HTTP $($response.StatusCode)" } catch { $ok = $false; $detail += "; HTTP failed: $($_.Exception.Message)" }
        } elseif ($forward.Name -eq 'operations-api') {
            try { $response = Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:$($forward.LocalPort)/api/v1/devices" -TimeoutSec 5; $ok = ($response.StatusCode -ge 200 -and $response.StatusCode -lt 300); $detail += "; HTTP $($response.StatusCode)" } catch { $ok = $false; $detail += "; HTTP failed: $($_.Exception.Message)" }
        } elseif ($forward.Name -eq 'mosquitto') {
            $ok = Test-Mqtt ([int]$forward.LocalPort); $detail += "; MQTT CONNACK=$ok"
        } elseif ($forward.Name -eq 'kafka') {
            $ok = Test-Kafka ([int]$forward.LocalPort); $detail += "; Kafka ApiVersions=$ok"
        } else {
            $ok = Test-Postgres ([int]$forward.LocalPort) $TimescaleUser $TimescaleDatabase $TimescalePassword; $detail += "; PostgreSQL SELECT 1=$ok"
        }
        $results += [pscustomobject]@{ Service = $forward.Name; Passed = [bool]$ok; Detail = $detail }
    }
    $results | Format-Table -AutoSize
    $failed = @($results | Where-Object { -not $_.Passed })
    if ($failed.Count -gt 0) { Fail "One or more forwarded service checks failed. See tunnel log and the table above." }
}

function Show-Status([object] $Discovery) {
    $state = Read-State
    if ($null -eq $state) { Write-Info "Stopped. No state file at $StatePath."; return }
    $alive = Test-SshTunnelProcess ([int]$state.Pid) $state.Machine @($state.Forwards)
    [pscustomobject]@{
        State = if ($alive) { 'Running' } else { 'Stale' }
        Pid = $state.Pid
        Machine = $state.Machine.Name
        SshEndpoint = "$($state.Machine.User)@$($state.Machine.Host):$($state.Machine.Port)"
        Key = $state.Machine.IdentityPath
        Log = $state.LogPath
        StateFile = $StatePath
        Forwards = (@($state.Forwards) | ForEach-Object { "$($_.LocalAddress):$($_.LocalPort)->$($_.RemoteAddress):$($_.RemotePort)" }) -join '; '
    } | Format-List
    if (-not $alive) { Write-Info 'The recorded process is stale; Start will remove it and rediscover current container IPs.' }
    if ($null -ne $Discovery) { Get-ListeningOwners @($Discovery.Forwards | ForEach-Object { $_.LocalPort }) | Format-Table -AutoSize }
}

if ($Action -eq 'Stop') {
    Stop-Tunnel
    exit 0
}

if ($Action -eq 'Test') {
    $state = Read-State
    if ($null -eq $state) { Fail "No recorded tunnel state at $StatePath. Start the tunnel first." }
    if (-not (Test-SshTunnelProcess ([int]$state.Pid) $state.Machine @($state.Forwards))) {
        Fail "Recorded tunnel PID $($state.Pid) is not running. Run Start to rediscover and recreate it."
    }
    # Test the exact persisted local ports. This matters when a caller used
    # non-default ports to avoid a stale host-side VirtioProxy listener.
    Invoke-ForwardChecks @($state.Forwards)
    exit 0
}

if ($Action -eq 'Status') {
    $statusDiscovery = $null
    try { $statusDiscovery = Get-Discovery (Get-Machine) } catch { }
    Show-Status $statusDiscovery
    exit 0
}

$machine = Get-Machine
$discovery = Resolve-ForwardPorts (Get-Discovery $machine)
switch ($Action) {
    'Start' { Start-Tunnel $discovery }
    'Restart' { Stop-Tunnel; Start-Tunnel $discovery }
}
