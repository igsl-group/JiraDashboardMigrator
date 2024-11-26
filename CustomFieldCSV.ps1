$json = Get-Content -Path CustomField.DataCenter.json | ConvertFrom-Json 
$data = @($json | Select-Object id, name, custom, @{Name = 'fieldType'; Expression = {$_.schema | Select-Object -ExpandProperty custom}})
$data | Export-CSV -NoTypeInformation CustomField.DataCenter.csv
