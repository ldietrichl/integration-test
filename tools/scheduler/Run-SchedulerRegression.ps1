#requires -Version 5.1
[CmdletBinding()]
param(
    [ValidateSet('read-only','fixtures','jobs','full')][string]$Phase='read-only',
    [switch]$MutationWindowApproved
)
$ErrorActionPreference='Stop'
if($Phase -ne 'read-only' -and -not $MutationWindowApproved){
    throw 'Mutating phases require an agreed shared-stand window and -MutationWindowApproved.'
}
$project=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$wrapper=Join-Path $project 'gradlew.bat'
if(-not (Test-Path -LiteralPath $wrapper -PathType Leaf)){throw 'Project gradlew.bat is missing.'}
$task='schedulerRegression'
$oldPhase=[Environment]::GetEnvironmentVariable('SCHEDULER_REGRESSION_PHASE','Process')
$oldApproval=[Environment]::GetEnvironmentVariable('SCHEDULER_MUTATION_WINDOW_APPROVED','Process')
try{
    $env:SCHEDULER_REGRESSION_PHASE=$Phase
    $env:SCHEDULER_MUTATION_WINDOW_APPROVED=([bool]$MutationWindowApproved).ToString().ToLowerInvariant()
    Push-Location -LiteralPath $project
    try{
  & $wrapper $task ('-Pscheduler.regression.phase='+$Phase) ('-Pscheduler.mutation.window.approved='+([bool]$MutationWindowApproved).ToString().ToLowerInvariant()) --rerun-tasks --no-daemon --console=plain
        if($LASTEXITCODE -ne 0){throw ('Scheduler regression failed; exit code '+$LASTEXITCODE+'. Preserve journals and reports.')}
    }finally{Pop-Location}
}finally{
    [Environment]::SetEnvironmentVariable('SCHEDULER_REGRESSION_PHASE',$oldPhase,'Process')
    [Environment]::SetEnvironmentVariable('SCHEDULER_MUTATION_WINDOW_APPROVED',$oldApproval,'Process')
}
