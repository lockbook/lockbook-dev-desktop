package lockbook.dev

import java.io.File
import java.net.URI

import com.jcraft.jsch.{JSch, Session}
import org.eclipse.jgit.api.errors.TransportException
import org.eclipse.jgit.api.{Git, PullCommand}
import org.eclipse.jgit.lib.ProgressMonitor
import org.eclipse.jgit.transport.{
  JschConfigSessionFactory,
  OpenSshConfig,
  UsernamePasswordCredentialsProvider
}
import org.eclipse.jgit.util.FS

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

trait GitHelper {
  def cloneRepository(uri: String): Future[Git]
  def getRepositories: Future[List[Git]]
  def commitAndPush(message: String, git: Git): Future[Unit]
  def getRepoName(git: Git): String
  def pullCommand(git: Git, progressMonitor: ProgressMonitor): PullCommand
  def pull(pullCommand: PullCommand): Future[Unit]
  def deleteRepo(git: Git): Future[Unit]
}

class GitHelperImpl(gitCredentialHelper: GitCredentialHelper, fileHelper: FileHelper)
    extends GitHelper {

  val repoFolder = s"${App.path}/repos"

  override def cloneRepository(uri: String): Future[Git] =
    getCredentials(new URI(uri))
      .flatMap(
        credentials =>
          try {
            Success(
              Git
                .cloneRepository()
                .setURI(uri)
                .setDirectory(new File(s"$repoFolder/${uriToFolder(uri)}"))
                .setCredentialsProvider(credentials)
                .call()
            )
          } catch {
            case e: TransportException =>
              gitCredentialHelper.incorrectCredentials(new URI(uri).getHost)
              Failure(new Error("Saved Credentials were invalid, please retry."))
            case e: Exception =>
              Failure(new Error(e))
          }
      )

  override def commitAndPush(message: String, git: Git): Future[Unit] = {
    getCredentials(getRepoURI(git))
      .flatMap(credentials => {
        try {
          git.add().addFilepattern(".").call()
          git.commit().setMessage(message).call()

          git
            .push()
            .setCredentialsProvider(credentials)
            .call()

          Success(Unit)
        } catch {
          case e: Exception =>
            Failure(new Error(e))
        }
      })
  }

  override def getRepositories: List[Git] = {
    val repos = new File(repoFolder)
    if (repos.exists()) {
      repos
        .listFiles(_.isDirectory)
        .toList
        .map(file => Git.open(file))
    } else {
      List()
    }
  }

  override def pullCommand(git: Git, progressMonitor: ProgressMonitor): PullCommand = {
    getCredentials(git)
      .map(credentials => {
        git
          .pull()
          .setCredentialsProvider(credentials)
          .setProgressMonitor(progressMonitor)
      })
      .get
  }

  override def pull(pullCommand: PullCommand): Future[Unit] = Future {
    pullCommand.call()
  }

  override def deleteRepo(git: Git): Future[Unit] = Future {
    val file = git.getRepository.getWorkTree
    fileHelper.recursiveFileDelete(file)
  }

  private def getCredentials(git: Git): Future[UsernamePasswordCredentialsProvider] =
    getCredentials(getRepoURI(git))

  private def getCredentials(uri: URI): Future[UsernamePasswordCredentialsProvider] = {
    gitCredentialHelper
      .getCredentials(uri.getHost)
      .map(
        gitCredentials =>
          new UsernamePasswordCredentialsProvider(gitCredentials.username, gitCredentials.password)
      )
  }

  def uriToFolder(uri: String): String = {
    val folderName = uri.split("/").last
    if (folderName.endsWith(".git")) {
      folderName.substring(0, folderName.length - 4)
    } else {
      folderName
    }
  }

  def getRepoName(git: Git): String = {
    val repoUrl = git.getRepository.getConfig
      .getString("remote", "origin", "url")

    uriToFolder(repoUrl)
  }

  def getRepoURI(git: Git): URI = {
    val repoUrl = git.getRepository.getConfig
      .getString("remote", "origin", "url")

    new URI(repoUrl)
  }
}

class CustomConfigSessionFactory extends JschConfigSessionFactory {
  override protected def getJSch(hc: OpenSshConfig.Host, fs: FS): JSch = {
    val jsch = super.getJSch(hc, fs)
    jsch.removeAllIdentity()
    jsch.addIdentity("~/.ssh/id_rsa")
    jsch
  }

  override def configure(hc: OpenSshConfig.Host, session: Session): Unit = {}
}
