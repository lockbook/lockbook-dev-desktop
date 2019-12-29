package lockbook.dev

import java.util.Optional
import java.util.concurrent.TimeUnit

import javafx.collections.FXCollections
import javafx.geometry.Insets
import javafx.scene.control
import javafx.scene.control.{TextFormatter, _}
import javafx.scene.layout.GridPane

import scala.concurrent.duration.FiniteDuration

class SettingsUi(settingsHelper: SettingsHelper, fileHelper: FileHelper) {

  def showView(): Unit = {

    val dialog: Dialog[LockbookSettings] = new Dialog[LockbookSettings]()

    dialog.getDialogPane.getButtonTypes.addAll(
      ButtonType.APPLY,
      ButtonType.CANCEL
    )

    val gridPane = new GridPane
    showViewHelper(dialog, gridPane)

    val result: Optional[LockbookSettings] = dialog.showAndWait()

    if (result.isPresent) {
      SettingsHelper.constructJson(result.get(), fileHelper)
    }
  }

  private def showViewHelper(dialog: Dialog[LockbookSettings], gridPane: GridPane): Unit = {
    val stylesBox        = new ComboBox[Theme](FXCollections.observableArrayList(Light))
    val autoLockIntField = new TextField()
    val autoLockCheckBox = new control.CheckBox()

    stylesBox.getSelectionModel.select(settingsHelper.getTheme) // TODO: Optimize
    if (settingsHelper.getAutoLock.time.isEmpty) {
      autoLockCheckBox.setSelected(true)
      autoLockIntField.setDisable(true)
    } else {
      autoLockIntField.setText(settingsHelper.getAutoLock.time.get.toMinutes.toString)
    }

    autoLockCheckBox.setOnAction(
      _ =>
        if (autoLockCheckBox.isSelected) autoLockIntField.setDisable(true)
        else autoLockIntField.setDisable(false)
    )

    autoLockIntField.setTextFormatter(new TextFormatter[String]((change: TextFormatter.Change) => {
      change.setText(change.getText.replaceAll("""\D+""", ""))

      if (autoLockIntField.getText.length > 1)
        change.setText("")

      change
    }))

    dialog.setTitle("Settings") // use settingshelper across, then advanced capabil.
    dialog.setHeaderText("Settings")
    dialog.setGraphic(null)

    gridPane.setHgap(10)
    gridPane.setVgap(10)
    gridPane.setPadding(new Insets(10))

    gridPane.add(new Label("Styles"), 0, 1)
    gridPane.add(stylesBox, 1, 1)
    gridPane.add(new Label("Auto Lock Time"), 0, 2)
    gridPane.add(autoLockIntField, 1, 2)
    gridPane.add(autoLockCheckBox, 2, 2)
    gridPane.add(new Label("Restart to have settings take effect"), 0, 3)

    dialog.getDialogPane.getStylesheets.add(settingsHelper.getTheme.fileName)
    dialog.getDialogPane.setContent(gridPane)

    dialog.setResultConverter(
      dialogButton => {
        if (dialogButton == ButtonType.APPLY)
          LockbookSettings(
            Some(stylesBox.getValue),
            Some(
              if (autoLockCheckBox.isSelected || autoLockIntField.getText.isEmpty) AutoLock(None)
              else AutoLock(Some(FiniteDuration(autoLockIntField.getText.toInt, TimeUnit.MINUTES)))
            )
          )
        else null
      }
    )
  }
}
