# Jenkins Shared Library: GitHub App
This library allows Jenkins pipelines to authenticate as a GitHub App. 

## Getting Started
This example creates a new `Credential` called `jenkins-auto-bot` and then authenticates the rest of the pipline using this credential.  The plugins required for this sample are:

#### Plugins
- Generic Webhook Trigger
- Pipeline
- Git

#### Script Permissions
If running this as a shared pipeline you may have to enable permissions to `Allow In-Process Script Approval`. These permissions are required when using shared libraries as the pipelines are executed in `sandbox` mode. You can add the functions directly to a pipeline file to avoid this.
- `method com.cloudbees.plugins.credentials.CredentialsStore addCredentials com.cloudbees.plugins.credentials.domains.Domain com.cloudbees.plugins.credentials.Credentials`
- `method com.cloudbees.plugins.credentials.SystemCredentialsProvider getStore`
- `method java.lang.String getBytes`
- `new com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl com.cloudbees.plugins.credentials.CredentialsScope java.lang.String java.lang.String java.lang.String java.lang.String`
- `new java.lang.String byte[]`
- `staticField com.cloudbees.plugins.credentials.CredentialsScope GLOBAL`
- `staticMethod com.cloudbees.plugins.credentials.SystemCredentialsProvider getInstance`
- `staticMethod com.cloudbees.plugins.credentials.domains.Domain global`
- `staticMethod org.apache.commons.codec.binary.Base64 encodeBase64 byte[]`
- `staticMethod org.codehaus.groovy.runtime.DefaultGroovyMethods print groovy.lang.Closure java.lang.Object`

#### Sample Pipeline
```groovy
// this library is implicitly loaded
// To load explicitly ensure you configure
// the library and add the following:
// @Library('primetheus-github-app')
import com.cloudbees.plugins.credentials.impl.*;
import com.cloudbees.plugins.credentials.*;
import com.cloudbees.plugins.credentials.domains.*;

pipeline {
    agent any
    triggers {
      GenericTrigger(
        causeString: 'Generic Cause', 
        genericVariables: [[defaultValue: '', key: 'hook', regexpFilter: '', value: '$']], 
        printPostContent: true, 
        regexpFilterExpression: '', 
        regexpFilterText: '', 
        token: 'jenkins-bot-webhook-trigger')
    }
    stages {
        stage('Authenticate GitHub App') {
            steps {
                script {
                    payload = readJSON(text: "${hook}")
                    githubHost = payload.repository.url.split('/')[2]
                    installations = githubApp.getInstallations(githubHost)
                    installation = githubApp.get_installation(payload.repository.owner.login, installations)
                    auth = githubApp.getInstallationToken(installation.access_tokens_url)
                }
            }
        }
        stage('Create Credential') {
            steps {
                script {
                    uuid = java.util.UUID.randomUUID().toString()
                    Credentials c = (Credentials) new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, "jenkins-auto-bot", "jenkins-auto-bot", "x-access-token", auth.token)
                    SystemCredentialsProvider.getInstance().getStore().addCredentials(Domain.global(), c)
                }
            }
        }
        stage('Test an HTTPS Clone') {
            steps {
                script {
                    git(credentialsId: 'jenkins-auto-bot', url: payload.repository.clone_url)
                }
            }
        }
    }
}
```

## Getting your own token
If you use the [`GitHub Authentication`](https://plugins.jenkins.io/github-oauth/) plugin along with this library you can get a temporary Personal Access Token for yourself.

#### Print the username for the build (yours)
```groovy
@Library('primetheus-gitub-app')

echo getBuildUser()
```

#### Print your GitHub token for this build session
```groovy
@Library('primetheus-github-app')

echo getAccessToken()
```
