@Grab("io.jsonwebtoken:jjwt")
import hudson.model.User
import hudson.util.Secret
import io.jsonwebtoken.Jwts
import static io.jsonwebtoken.SignatureAlgorithm.RS256
import static jenkins.bouncycastle.api.PEMEncodable.decode
import org.bouncycastle.crypto.util.PrivateKeyFactory
import org.jenkinsci.plugins.GithubAccessTokenProperty

/**
 * https://developer.github.com/v3/apps/available-endpoints/
 */

def getBuildUser() {
  /**
   * Provides the username of the user who kicked off the build
   * 
   * @return The username of the person who kicked off the build
   */
  return currentBuild.rawBuild.getCause(Cause.UserIdCause).getUserId()
}

def getAccessToken() {
  /**
   * Provides access to the currently logged in user's PAT generated via OAuth
   * This method requires the GitHub Authentication plugin
   *
   * @link https://plugins.jenkins.io/github-oauth
   * @return The Personal Access Token generated at login for the current user
   */
  return User.get(getBuildUser()).getProperty(GithubAccessTokenProperty.class).getAccessToken()
}

def getJwt(String credential = 'jenkins-bot') {
  node {
    withCredentials([sshUserPrivateKey(credentialsId: credential,
                                       keyFileVariable: 'secret_key',
                                       usernameVariable: 'APP_ID')]) {
      iat = currentBuild.startTimeInMillis.toString()[0..9].toInteger()
      exp = (currentBuild.startTimeInMillis + (10 * 60)).toString()[0..9].toInteger()
      payload = "{\"iat\": ${iat},\"exp\": ${exp},\"iss\":${APP_ID}}"
      priv_key = readFile(file: secret_key).trim()
      Secret p = Secret.fromString('')
      @NonCPS
      key_pair = decode(priv_key, ((p)? p.plainText : null) as char[])
      key = key_pair.toPrivateKey()
      jwtToken = Jwts.builder()
         .setHeaderParam("typ","JWT")
         .setPayload(payload)
         .signWith(RS256, key)
         .compact();
      return jwtToken
    }
  }
}

def getInstallationToken(String access_tokens_url, String credential = 'jenkins-bot') {
  /**
   * Returns an Installation Token for use with GitHub Apps
   *
   * This method requires the HTTP Request plugin
   *
   * @link https://plugins.jenkins.io/http_request
   * @link https://developer.github.com/apps/building-github-apps/authenticating-with-github-apps/#authenticating-as-an-installation
   * @param installationId the installation ID for this GitHub App
   * @return the OAuth token for a given GitHub App installation
   */
  JwtToken = getJwt(credential)
  response = httpRequest(
    customHeaders: [
      [maskValue: false, name: 'Authorization', value: 'Bearer ' + JwtToken], 
      [maskValue: false, name: 'Accept', value: 'application/vnd.github.machine-man-preview+json']
    ],
    httpMode: 'POST',
    outputFile: 'token.json',
    responseHandle: 'NONE', 
    url: access_tokens_url)
  token = readJSON(file: 'token.json')
  return token
}

def getApp(String githubHost = 'api.github.com', String credential = 'jenkins-bot') {
  /**
   * Return information about the authenticated app
   *
   * This method requires the HTTP Request plugin
   *
   * @link https://plugins.jenkins.io/http_request
   * @link https://developer.github.com/apps/building-github-apps/authenticating-with-github-apps/#authenticating-as-a-github-app
   * @link https://developer.github.com/v3/apps/#get-the-authenticated-github-app
   * @return the GitHub App info
   */
  JwtToken = getJwt(credential)
  githubUrl = (githubHost == 'api.github.com') ? "https://${githubHost}/app" : "https://${githubHost}/api/v3/app"
  response = httpRequest(
    customHeaders: [
      [maskValue: false, name: 'Authorization', value: 'Bearer ' + JwtToken], 
      [maskValue: false, name: 'Accept', value: 'application/vnd.github.machine-man-preview+json']
    ],
    outputFile: 'app.json',
    responseHandle: 'NONE', 
    url: githubUrl)
  app = readJSON(file: 'app.json')
  return app
}

def getInstallations(String githubHost = 'api.github.com', String credential = 'jenkins-bot') {
  /**
   * Get a list of installations for this GitHub App
   *
   * This method requires the HTTP Request plugin
   *
   * @link https://plugins.jenkins.io/http_request
   * @link https://developer.github.com/v3/apps/#list-installations
   * @return the installation data for this GitHub App
   */
  JwtToken = getJwt(credential)
  githubUrl = (githubHost == 'api.github.com') ? "https://${githubHost}/app/installations" : "https://${githubHost}/api/v3/app/installations" 
  response = httpRequest(
    customHeaders: [
      [maskValue: false, name: 'Authorization', value: 'Bearer ' + JwtToken], 
      [maskValue: false, name: 'Accept', value: 'application/vnd.github.machine-man-preview+json']
    ],
    outputFile: 'installations.json',
    responseHandle: 'NONE', 
    url: githubUrl)
  installations = readJSON(file: 'installations.json')
  return installations
}

def getRepositories(String token, String repositories_url) {
  /**
   * Get a list of repositories available to this installation
   *
   * This method requires the HTTP Request plugin
   *
   * @link https://plugins.jenkins.io/http_request
   * @link https://developer.github.com/v3/apps/installations/#list-repositories
   * @return the repositories available to this GitHub App
   */
  response = httpRequest(
    customHeaders: [
      [maskValue: false, name: 'Authorization', value: 'Bearer ' + token], 
      [maskValue: false, name: 'Accept', value: 'application/vnd.github.machine-man-preview+json'],
      [maskValue: false, name: 'Accept', value: 'application/vnd.github.mercy-preview+json']
    ],
    outputFile: 'repositories.json',
    responseHandle: 'NONE', 
    url: repositories_url)
  repositories = readJSON(file: 'repositories.json')
  return repositories
}

@NonCPS // has to be NonCPS or the build breaks on the call to .each
def get_installation(login, installations) {
    def installation
    installations.each { item ->
        if (login == item.account.login) {
            installation = item
        }
    }
    return installation
}
