# Jenkins Pipeline Shared Library

This repository holds shared library for Jenkins Pipeline

## Import Library

### Configure Jenkins Pipeline Job

- In The `Pipeline Libraries` section, click `ADD`.
  - Give the library a name, for example, `zowe-jenkins-library`. Set `Default version` to `master`.
  - For `Retrieval method`, choose `Modern SCM`.
    - For `Source Code Management` section, choose `GitHub`.
    - Configure github connection to this repository

### Import Library in Jenkinsfile

If you didn't check `Load implicitly` when configuring the job, you need to use `@Library` to import it.

In your Jenkinsfile, you can add `@Library('zowe-jenkins-library') _` on top of the file to import everything.

Then you can use any methods/functions defined in the library. For example:

```
utils.conditionalStage('build', !isPullRequest) {
  // your stage code
}
```
