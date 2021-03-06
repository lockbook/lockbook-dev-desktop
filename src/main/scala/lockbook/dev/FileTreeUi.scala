package lockbook.dev

import java.io.File

import javafx.scene.control._
import javafx.scene.layout.BorderPane
import org.eclipse.jgit.api.Git

class FileTreeUi(fileHelper: FileHelper, dialogUi: DialogUi) {

  def getView(git: Git, onSelected: (Git, File) => Unit): BorderPane = {
    // Setup TreeView
    val treeView = new TreeView[File]
    treeView.setRoot(getViewHelper(git.getRepository.getWorkTree))
    treeView.setShowRoot(true)
    treeView.setCellFactory(_ => fileToTreeCell(git, onSelected))
    treeView.getSelectionModel.selectedItemProperty
      .addListener(
        (_, oldValue, newValue) =>
          if (newValue.isLeaf && oldValue != newValue && newValue.getValue.isFile)
            onSelected(git, newValue.getValue)
      )

    val root = new BorderPane

    root.setCenter(treeView)
    root
  }

  def getViewHelper(file: File): TreeItem[File] = {
    val item: TreeItem[File]            = new TreeItem[File](file)
    val childrenUnfiltered: Array[File] = file.listFiles

    if (childrenUnfiltered != null) {
      val children = childrenUnfiltered.filter(_.getName != ".git").sortBy(_.getName)
      for (child <- children) {
        item.getChildren.add(getViewHelper(child))
      }
    }
    item
  }

  private def fileToTreeCell(git: Git, onSelected: (Git, File) => Unit): TreeCell[File] =
    new TreeCell[File]() {
      override def updateItem(item: File, empty: Boolean): Unit = {
        super.updateItem(item, empty)
        if (item != null) {

          val contextMenu = new ContextMenu
          val delete      = new MenuItem("Delete")
          val newFile     = new MenuItem("New File")
          val newFolder   = new MenuItem("New Folder")

          val isRoot = super.getTreeView.getRoot.getValue.equals(item)
          if (!isRoot) {
            contextMenu.getItems.addAll(newFolder, newFile, delete)
          } else {
            getTreeItem.setExpanded(true)
            contextMenu.getItems.addAll(newFolder, newFile)
          }

          val enclosingFolderNode: TreeItem[File] = if (getTreeItem.getValue.isDirectory) {
            getTreeItem
          } else {
            getTreeItem.getParent
          }

          delete.setOnAction(_ => {
            fileHelper.recursiveFileDelete(item)
            getTreeItem.getParent.getChildren.remove(getTreeItem)
          })

          newFile.setOnAction(_ => insertFileOrFolder(item, isFile = true, enclosingFolderNode, getTreeView))
          newFolder.setOnAction(_ => insertFileOrFolder(item, isFile = false, enclosingFolderNode, getTreeView))

          setContextMenu(contextMenu)
          setText(item.getName)
        } else {
          setText("")
        }
      }
    }

  private def insertFileOrFolder(
      item: File,
      isFile: Boolean,
      enclosingFolderNode: TreeItem[File],
      treeView: TreeView[File]
  ): Unit = {
    val parentDirectory = if (!item.isDirectory) item.getParentFile else item
    newFileOrFolderDialogResult(isFile).map(name => s"${parentDirectory.getAbsolutePath}/$name") match {
      case Some(newFileName) =>
        val newFile = new File(newFileName)
        if (isFile) newFile.createNewFile()
        else newFile.mkdirs()

        val newTreeItem = new TreeItem[File](newFile)
        enclosingFolderNode.getChildren.add(newTreeItem)
        enclosingFolderNode.setExpanded(true)

        treeView.getSelectionModel.select(newTreeItem)

      case None =>
    }
  }

  private def newFileOrFolderDialogResult(isFile: Boolean): Option[String] = {
    val fileOrFolder = if (isFile) "file" else "folder"

    val title   = s"Create new $fileOrFolder"
    val header  = s"Enter $fileOrFolder name"
    val content = "Name:"

    dialogUi.askUserForString(title, header, content)
  }

}
