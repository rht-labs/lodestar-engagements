# Development on OpenShift

## Getting Started With Helm

This directory contains a Helm chart which can be used to deploy a development version of this app for rapid testing.

Before you use it, you will need to download & install Helm 3.

If you are not familiar with Helm - how to configure it and run - you can start with this quickstart:

[https://helm.sh/docs/intro/quickstart](https://helm.sh/docs/intro/quickstart)

## Using This Chart

1. Clone the target repo:

```
git clone https://github.com/rht-labs/lodestar-engagements
```

2. Change into to the `deployment` directory:

```
cd lodestar-engagements/deployment
```

3. Deploy using the following Helm command:

```shell script
  helm template . \
    --values values-dev.yaml \
    --set api.gitlab=<gitlabUrl> \
    --set tokens.gitlab=<gitlabToken> \
    --set git.uri=<your fork> \
    --set git.ref=<your branch> \
    --gitlab.deployKey=<deployKeyId> \
    --gitlab.engagementRepositoryId=<top-level-repo-id> \
    --set mongodbServiceName=lodestar-backend-mongodb \
    --set mongodbUser=<your-mongodb-user> \
    --set mongodbPassword=<your-mongodb-password> \
    --set mongodbDatabase=engagements \
    --set mongodbAdminPassword=<your-mongodb-admin-password> \
  | oc apply -f -
```

It accepts the following variables

| Variable  | Use  |
|---|---|
| `git.uri`  | The HTTPS reference to the repo (your fork!) to build  |
| `git.ref`  | The branch name to build  |
| `api.config`  | The base URL of the config service  |
| `api.participants` | The base URL of the participants service |
| `api.gitlab`  | The base URL of the GitLab instance to use  |
| `gitlab.engagementRepositoryId` | The parent group id of all engagements |
| `gitlab.deployKey` | The id of the deploy to assign to projects |
| `tokens.gitlab`  | The access token to use to auth against GitLab  |
| `mongodbServiceName` | MongoDB service name |
| `mongodbUser` | Application user for MongoDB |
| `mongodbPassword` | Application user password for MongoDB |
| `mongodbDatabase` | Application database name |
| `mongodbAdminPassword` | Admin password for MongoDB |

This will spin up all the usual resources that this service needs in production, plus a `BuildConfig` configured to build it from source from the Git repository specified. To trigger this build, use `oc start-build lodestar-hosting`. To trigger a build from the source code on your machine, use `oc start-build lodestar-hosting --from-dir=. -F` 
