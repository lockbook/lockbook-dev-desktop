package lockbook.dev

import javafx.application.Platform
import javafx.collections.ObservableList
import javafx.scene.Scene
import javafx.scene.control.Label

import scala.concurrent.ExecutionContext.Implicits.global

class CloneRepoDialog(gitHelper: GitHelper, dialogUi: DialogUi, settingsHelper: SettingsHelper) {

  def showDialog(repoList: ObservableList[RepositoryCell], scene: Scene): Unit =
    dialogUi
      .askUserForString("Clone", "Clone a repository", "Repository URL")
      .foreach(url => {
        DoInBackgroundWithMouseSpinning(
          dialogUi,
          name = "Clone",
          task = () =>
            gitHelper
              .cloneRepository(url)
              .map(newRepo => Platform.runLater(() => repoList.addAll(RepositoryCell(newRepo, new Label)))),
          scene = scene
        )
      })
}
