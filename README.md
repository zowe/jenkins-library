# Jenkins Pipeline Shared Library

This repository holds shared library for Jenkins Pipeline

## Import Library

The Jenkins library requires sophisticated operation on Jenkins objects, which requires to be setup as `Global Pipeline Libraries` to avoid extra `In-process Script Approval`.

### Configure Jenkins Pipeline Job

- In The `Manage Jenkins` - `Configure System` section, find `Global Pipeline Libraries` and click `ADD`.
  - Give the library a name, for example, `jenkins-library`. Set `Default version` to `master`.
  - For `Retrieval method`, choose `Modern SCM`.
    - For `Source Code Management` section, choose `GitHub`.
    - Configure github connection to this repository

### Import Library in Jenkinsfile

If you didn't check `Load implicitly` when configuring the job, you need to use `@Library` to import it.

In your Jenkinsfile, you can add `def lib = library("jenkins-library").org.zowe.jenkins_shared_library
` on top of the file to import library.

Then you can use any classes/methods defined in the library. For example:

```
github = lib.scm.GitHub.new(this)
github.init([...])
github.commit('my test')
```

## Run Tests

To start test, run gradle command `gradle test`.

## Generate Documentation

Run gradle command `gradle groovydoc` to generate documentation.

## Relase Process Design

All below scenarios are based on `master` version `v2.3.4` as example, timestamp string example is `20190101000000`.

### Things We Want to Do

- Allow every branches to publish artifacts for debugging purpose.
- Release is only performed when we want to, so it's always started manually.

### Things We Want to Avoid

- To avoid creating a separated Jenkins job only for release purpose.
- To avoid modifying `package.json` or similar manifest file for versions with pre-release strings like adding `-latest.20190101000000` to the version `2.3.4` definition. The `package.json` will be kept as `2.3.4` until released.
- To avoid tagging the branch too often. We don't want to create daily tags `v2.1.0-beta.201902072129` to github repository.
- To avoid providing option to build any specific version. This specific version release may cause conflicts and confusion. If we want to build a specific version, we should update the code base (`package.json` or other manifest file) to the version we want to build. The release version shouldn't be decided when we start a release build.

### Release Options

- For each pipeline, we have 2 build parameters are only available to branches which can do a `release`:
  - `Perform Release`: boolean, default is _false_.
  - `Pre-Release String`: string, default is _empty_. This parameter will only be used if `Perform Release` is _true_. This parameter is required if the release is not happened on `master` or `v?.x/master` branch.
- By default, only `master`, `v?.x/master`, `staging` and `v?.x/staging` branches can do a release. This can be changed by `pipeline.addReleaseBranches(...branches)` or `pipeline.setReleaseBranches(...branches)`.
- For NPM packages, each branch can have a npm publish tag name when performaing release build. By default, `master`'s tag is `latest`, `staging`'s tag is `dev`. For branches do not have publish tag defined, or branches is not in release build, npm publish will use default `snapshot` tag name, and the version will have normalized branch name included. For example, `staging` branch **non-release** build will generate a version `v2.3.4-staging-snapshot.20190101000000` release on `snapshot` npm tag. `staging` branch **release** build will generate a version `v2.3.4-dev.20190101000000` release on `dev` tag. And a branch `feature/lts` build will generate a version `v2.3.4-feature-lts-snapshot.20190101000000` release on `snapshot` tag.
- For pax/zip packages, each branch can also have a release tag name. By default, `master`'s tag is 'snapshot'. If it's not defined, artifactory publish will use branch name as tag name. For example, `master` **non-release** build will publish artifact to `libs-snapshot-local/org/zowe/<component>/2.3.4-snapshot/` and **release** build will publish to `libs-release-local/org/zowe/<component>/2.3.4/`. `staging` **non-release** build will publish artifact to `libs-snapshot-local/org/zowe/<component>/2.3.4-staging/` and **release** build will publish to `libs-release-local/org/zowe/<component>/2.3.4-rc1/`, the `rc1` is the pre-release string which is required.

### Pipeline Build Scenarios

- **Scenario #1**
  - Build on `master` with default parameter values (`Perform Release` = _false_, `Pre-Release String` = _empty_)
    - for npm package, it will be published to `npm-local-release` as `v2.3.4-snapshot-master.20190101000000` with npm tag `snapshot`.
    - for pax/zip(gradle) package, it will be published to `libs-snapshot-local/org/zowe/<component>/2.3.4-snapshot/` as `<component>-2.3.4-snapshot.20190101000000.(pax|zip)`.
  - No github tag will be created.
  - No version bump will be performed on GitHub.
- **Scenario #2**
  - Build on `master` with (`Perform Release` = _true_, `Pre-Release String` = _beta.1_)
    - for npm package, it will be published to `npm-local-release` as `v2.3.4-beta.1` with npm tag `snapshot`.
    - for pax/zip(gradle) package, it will be published to `libs-release-local/org/zowe/<component>/2.3.4/` as `<component>-2.3.4-beta.1.(pax|zip)`.
  - Github tag `v2.3.4-beta.1` will be created.
  - No version bump will be performed on GitHub after release.
- **Scenario #3**
  - Build on `master` with (`Perform Release` = _true_, `Pre-Release String` = _empty_)
    - for npm package, it will be published to `npm-local-release` as `v2.3.4` with npm default tag `latest`.
    - for pax/zip(gradle) package, it will be published to `libs-release-local/org/zowe/<component>/2.3.4/` as `<component>-2.3.4.(pax|zip)`.
  - Github tag `v2.3.4` will be created.
  - A **PATCH** level version bump will be performed on GitHub. This is to avoid release the same version again. So after version bump, `master` has version of `2.4.0`, and a commit can be seen from github commit history. This commit should be merged/cherry-picked back to `staging`.
- **Scenario #dev-1**
  - Build on `staging` which **IS** a release branch with default parameter values (`Perform Release` = _false_, `Pre-Release String` = _empty_)
    - for npm package, it will be published to `npm-local-release` as `v2.3.4-staging-snapshot.20190101000000` with npm tag `snapshot`.
    - for pax/zip(gradle) package, it will be published to `libs-snapshot-local/org/zowe/<component>/2.3.4-staging/` as `<component>-2.3.4-staging.20190101000000.(pax|zip)`.
  - No github tag will be created.
  - No version bump will be performed on GitHub.
- **Scenario #dev-2**
  - Build on `staging` which **IS** a release branch with default parameter values (`Perform Release` = _true_, `Pre-Release String` = _rc.1_)
    - for npm package, it will be published to `npm-local-release` as `v2.3.4-rc.1` with npm tag `dev`.
    - for pax/zip(gradle) package, it will be published to `libs-snapshot-local/org/zowe/<component>/2.3.4-staging/` as `<component>-2.3.4-staging.20190101000000.(pax|zip)`.
  - No github tag will be created.
  - No version bump will be performed on GitHub after release.
- **Scenario #dev-3**
  - Build on `staging` which **IS** a release branch with default parameter values (`Perform Release` = _true_, `Pre-Release String` = _empty_)
  - Build will be rejected because pre-release string is empty.
- **Typical Scenario**
  - A NPM project has `latest`, `dev`, `v1-latest` and `v1-dev` tags. `v1` is a LTS version, current `master` is on `v2.3.4`.
  - There are 4 branches can do a release, the branch-tag mappings are:
    - `master` - `latest`
    - `staging` - `dev`
    - `v1.x/master` - `v1-latest`
    - `v1.x/staging` - `v1-dev`

### Release Scenarios

- **Release v2.3.4-rc.1**
  - Build `staging` with (`Perform Release` = _true_, `Pre-Release String` = _rc.1_).
  - Or build `master` with (`Perform Release` = _true_, `Pre-Release String` = _rc.1_), **not suggested**.
- **Release v2.3.4**
  - Build `master` with (`Perform Release` = _true_, `Pre-Release String` = _empty_).
- **Release v2.4.0**
  - Make a commit on `staging` to upgrade version to `2.4.0`.
  - Build `staging` with (`Perform Release` = _true_, `Pre-Release String` = _rc.1_), we should have `v2.4.0-rc.1`.
  - Create a Pull Request to merge `staging` into `master`. After merged, `master` should have a version definition of `2.4.0` instead of `2.3.4`.
  - build master with (`Perform Release` = _true_, `Pre-Release String` = _empty_), we should have `v2.4.0`.
