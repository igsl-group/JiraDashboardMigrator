$json = Get-Content -Path CustomField.DataCenter.json | ConvertFrom-Json 
$data = @($json | Select-Object id, name, custom, @{Name = 'fieldType'; Expression = {$_.schema | Select-Object -ExpandProperty custom}})
$data | Export-CSV -NoTypeInformation CustomField.DataCenter.csv

<#
Excel formula to categorize custom field type

=
IF($D2 = "", "Atlassian", 
IF(COUNTIF($D2, "com.atlassian.*")=1, "Atlassian", 
IF(COUNTIF($D2, "com.okapya.*")=1, "Checklist for Jira", 
IF(COUNTIF($D2, "com.valiantys*")=1, "SQL Feed", 
IF(COUNTIF($D2, "com.projectbalm.riskregister*")=1, "Risk Register", 
IF(COUNTIF($D2, "com.innovalog.jmcf*")=1, "Jira Misc Custom Field", 
IF(COUNTIF($D2, "com.coresoftlabs.sla_powerbox*")=1, "SLA Powerbox", 
IF(COUNTIF($D2, "com.thed.zephyr*")=1, "Zephyr Squad", 
IF(COUNTIF($D2, "com.onresolve.jira.groovy*")=1, "ScriptRunner", 
IF(COUNTIF($D2, "de.polscheit.jira.plugins.group-sign-off*")=1, "Group Sign-Off", 
IF(COUNTIF($D2, "de.stagil.jira.stagil-table-field*")=1, "Stagil Table", 
IF(COUNTIF($D2, "com.jibrok.jira.plugins.time-in-status*")=1, "Time in Status", 
IF(COUNTIF($D2, "com.pyxis.greenhopper.jira*")=1, "Atlassian", 
IF(COUNTIF($D2, "com.veracode*")=1, "Veracode", 
"Unrecognized"
))))))))))))))

#>
