package lockbook.dev

import javafx.collections.{FXCollections, ObservableList}
import javafx.scene.control._
import javafx.scene.input.{KeyCode, KeyCodeCombination, KeyCombination, KeyEvent}
import javafx.scene.layout._
import org.eclipse.jgit.api.Git

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class RepositoryUi(
    gitHelper: GitHelper,
    repositoryCellUi: RepositoryCellUi,
    dialogUi: DialogUi,
    cloneRepoDialog: CloneRepoDialog
) {

  val repoList: ObservableList[RepositoryCell] = FXCollections.observableArrayList[RepositoryCell]()

  def getView(onClick: Git => Unit): BorderPane = {
    val borderPane = new BorderPane // TODO does this still need to be a BorderPane?
    val listView   = new ListView[RepositoryCell]

    borderPane.setId("repoList")

    Future {
      repoList.removeAll(repoList)
      gitHelper.getRepositories
        .map(RepositoryCell(_, new Label))
        .map(repoList.add)

      setRepoStatuses()
    }

    listView
      .cellFactoryProperty()
      .setValue(
        _ =>
          repositoryCellUi
            .getListCell(onClick, delete(listView), () => cloneRepoDialog.showDialog(repoList, listView.getScene))
      )

    listView.getSelectionModel
      .selectedItemProperty()
      .addListener((_, old, newVal) => {
        if (old != newVal) {
          onClick(newVal.git) // TODO: fails if new val is null, happens if you delete the last repository
        }
      })

    listView.setItems(repoList)

    val cloneRepo = new MenuItem("Clone Repository")
    listView.setContextMenu(new ContextMenu(cloneRepo))
    cloneRepo.setOnAction(_ => {
      cloneRepoDialog.showDialog(repoList, listView.getScene)
    })

    addSyncListener(listView)

    borderPane.setCenter(listView)
    borderPane
  }

  def addSyncListener(value: ListView[RepositoryCell]): Unit = {
    val saveKeyCombo = new KeyCodeCombination(KeyCode.S, KeyCombination.META_DOWN)

    value
      .sceneProperty()
      .addListener((_, _, newValue) => { // When this ListView is attached to a scene
        if (newValue != null) {
          newValue.addEventHandler(
            KeyEvent.KEY_PRESSED,
            (event: KeyEvent) => {               // An event has happened
              if (saveKeyCombo.`match`(event)) { // Our shortcut is matched

                DoInBackgroundWithMouseSpinning( // Push is performed in background
                  dialogUi,
                  name = "Pushing changes",
                  task = () => gitHelper.commitAndPush("", value.getSelectionModel.getSelectedItem.git),
                  value.getScene
                )

              }
            }
          )
        }
      })
  }

  def delete(list: ListView[RepositoryCell])(repositoryCell: RepositoryCell): Unit = {
    gitHelper.deleteRepo(repositoryCell.git)
    list.getItems.remove(repositoryCell)
  }

  def setRepoStatuses(): Unit = Future {
    repoList
      .stream()
      .forEach(calculateStatus)
  }

  def calculateStatus(repoCell: RepositoryCell): Unit = {
    RepositoryCell.calculateStatus(repoCell, gitHelper)
  }
}
