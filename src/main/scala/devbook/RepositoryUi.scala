package devbook

import javafx.collections.{FXCollections, ObservableList}
import javafx.geometry.Insets
import javafx.scene.Scene
import javafx.scene.control._
import javafx.scene.layout._
import javafx.stage.Stage
import org.eclipse.jgit.api.Git

import scala.util.{Failure, Success}

class RepositoryUi(gitHelper: GitHelper) {

  def getView(onClick: Git => Unit): BorderPane = {
    val borderPane    = new BorderPane
    val newRepoButton = new Button("New Repository")
    val footer        = new HBox(newRepoButton)
    val repoList      = FXCollections.observableArrayList[Git]()
    val listView      = new ListView[Git]

    gitHelper.getRepositories.foreach(repoList.add)

    listView.setItems(repoList)
    listView
      .cellFactoryProperty()
      .setValue(_ => getListCell(onClick))

    newRepoButton.setOnAction(_ => {
      importRepoDialog(repoList)
    })

    borderPane.setCenter(listView)
    borderPane.setBottom(footer)
    borderPane
  }

  private def importRepoDialog(repoList: ObservableList[Git]): Unit = {
    // Dialog
    val dialog = new Stage
    dialog.setTitle("Add repository to Lockbook")

    val grid = new GridPane
    grid.setHgap(10)
    grid.setVgap(10)

    val repositoryURL = new TextField

    grid.add(new Label("Repository Url:"), 0, 0)
    grid.add(repositoryURL, 1, 0)

    val cancel = new Button("Cancel")
    cancel.setOnAction(_ => {
      dialog.close()
    })
    grid.add(cancel, 1, 1)

    val clone = new Button("Clone")
    clone.setOnAction(_ => {

      val repo = gitHelper.cloneRepository(repositoryURL.getText) match {
        case Success(value) =>
          repoList.add(value)
        case Failure(error) =>
          println(error) // TODO
      }
      dialog.close()
    })
    grid.add(clone, 2, 1)

    grid.setPadding(new Insets(20, 150, 10, 10))

    dialog.setScene(new Scene(grid, 500, 300))
    dialog.show()
  }

  private def getListCell(onClick: Git => Unit): ListCell[Git] = {
    val listCell: ListCell[Git] = new ListCell[Git]() {
      override def updateItem(item: Git, empty: Boolean): Unit = {
        super.updateItem(item, empty)

        if (empty || item == null) setText(null)
        else
          setText(gitHelper.getRepoName(item))
      }
    }

    listCell.setOnMouseClicked(_ => {
      if (!listCell.isEmpty) {
//        listCell.setBackground(
//          new Background(new BackgroundFill(Color.BLUE, CornerRadii.EMPTY, Insets.EMPTY))
//        )
        onClick(listCell.getItem)
      }
    })
    listCell
  }

}
