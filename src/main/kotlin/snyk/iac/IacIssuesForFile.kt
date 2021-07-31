package snyk.iac

class IacIssuesForFile {
    lateinit var infrastructureAsCodeIssues: Array<IacIssue>
    lateinit var targetFile: String
    lateinit var packageManager: String

    val uniqueCount: Int get() = infrastructureAsCodeIssues.groupBy { it.id }.size
}

class IacIssue {
    lateinit var id: String
    lateinit var title: String
    lateinit var severity: String

    lateinit var publicId: String
    lateinit var documentation: String
    lateinit var lineNumber: Integer

    lateinit var issue: String
    lateinit var impact: String
    var resolve: String? = null

    lateinit var references: List<String>
    lateinit var path: List<String>
}

/* Real json Example:

  {
    "meta": {
      "isPrivate": true,
      "isLicensesEnabled": false,
      "ignoreSettings": null,
      "org": "artsiom.chapialiou",
      "projectId": "",
      "policy": ""
    },
    "filesystemPolicy": false,
    "vulnerabilities": [],
    "dependencyCount": 0,
    "licensesPolicy": null,
    "ignoreSettings": null,
    "targetFile": "terraform\\aws\\spot_fleet_root_block_no_encryption.tf",
    "projectName": "infrastructure-as-code-goof",
    "org": "artsiom.chapialiou",
    "policy": "",
    "isPrivate": true,
    "targetFilePath": "D:\\TestProjects\\infrastructure-as-code-goof\\terraform\\aws\\spot_fleet_root_block_no_encryption.tf",
    "packageManager": "terraformconfig",
    "path": "D:\\TestProjects\\infrastructure-as-code-goof",
    "projectType": "terraformconfig",
    "ok": false,
    "infrastructureAsCodeIssues": [
      {
        "severity": "medium",
        "resolve": "Set `root_block_device.encrypted` attribute to `true`",
        "id": "SNYK-CC-TF-53",
        "impact": "That should someone gain unauthorized access to the data they would be able to read the contents.",
        "msg": "resource.aws_spot_fleet_request[unencrypted].launch_specification.root_block_device.encrypted",
        "remediation": {
          "cloudformation": "Set `BlockDeviceMappings.Encrypted` attribute of root device to `true`",
          "terraform": "Set `root_block_device.encrypted` attribute to `true`"
        },
        "subType": "EC2",
        "issue": "The root block device for ec2 instance is not encrypted",
        "publicId": "SNYK-CC-TF-53",
        "title": "Non-Encrypted root block device",
        "references": [
          "https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/RootDeviceStorage.html",
          "https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/EBSEncryption.html",
          "https://aws.amazon.com/premiumsupport/knowledge-center/cloudformation-root-volume-property/",
          "https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/device_naming.html"
        ],
        "isIgnored": false,
        "iacDescription": {
          "issue": "The root block device for ec2 instance is not encrypted",
          "impact": "That should someone gain unauthorized access to the data they would be able to read the contents.",
          "resolve": "Set `root_block_device.encrypted` attribute to `true`"
        },
        "lineNumber": 5,
        "documentation": "https://snyk.io/security-rules/SNYK-CC-TF-53",
        "path": [
          "resource",
          "aws_spot_fleet_request[unencrypted]",
          "launch_specification",
          "root_block_device",
          "encrypted"
        ]
      }
    ]
  }

 */
