import javafx.application.Application
import javafx.application.Platform
import javafx.beans.Observable
import javafx.embed.swing.SwingFXUtils
import javafx.embed.swing.SwingFXUtils.fromFXImage
import javafx.embed.swing.SwingFXUtils.toFXImage
import javafx.event.EventHandler
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.SnapshotParameters
import javafx.scene.canvas.Canvas
import javafx.scene.control.*
import javafx.scene.image.WritableImage
import javafx.scene.input.*
import javafx.scene.layout.HBox
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.scene.text.Text
import javafx.scene.transform.Scale
import javafx.stage.FileChooser
import javafx.stage.Stage
import javafx.util.converter.IntegerStringConverter
import java.awt.Rectangle
import java.awt.Robot
import java.awt.Toolkit
import java.io.*
import java.util.*
import java.util.function.UnaryOperator
import javax.imageio.ImageIO
import kotlin.concurrent.schedule
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min


class ScreenshotShooter : Application() {

    private var brushSize = 10
    private var isCropping = false
    private var firstX = 0.0
    private var firstY = 0.0
    private var lastX = 1.0
    private var lastY = 0.0

    override fun start(primaryStage: Stage) {
        val makeScreenshotMenuItem = MenuItem("Создать скриншот")
        val openImageMenuItem = MenuItem("Открыть изображение")
        val defaultSaveImageMenuItem = MenuItem("Сохранить изображение в \"Документы\"")
        val saveImageMenuItem = MenuItem("Сохранить изображение")
        val exitImageMenuItem = MenuItem("Очистить экран")
        val fileMenu = Menu("Файл")
        fileMenu.items.addAll(
            makeScreenshotMenuItem,
            openImageMenuItem,
            defaultSaveImageMenuItem,
            saveImageMenuItem,
            exitImageMenuItem
        )

        val menuBar = MenuBar(
            fileMenu
        )
        //
        val cropImageButton = Button("Обрезать")
        //
        val colorOfBrushPicker = ColorPicker(Color.color(0.0, 0.0, 0.0))
        //
        val sizeOfBrushTextInputLabel = Label("px")
        val sizeOfBrushTextInput = TextField("10")
        sizeOfBrushTextInput.alignment = Pos.CENTER_RIGHT
        val integerFilter: UnaryOperator<TextFormatter.Change?> =
            UnaryOperator { change: TextFormatter.Change? ->
                val newText: String? = change?.controlNewText
                if (newText != null) {
                    if (newText.matches(regex = Regex("-?([1-9][0-9]*)?"))) {
                        return@UnaryOperator change
                    }
                }
                null
            }

        sizeOfBrushTextInput.textFormatter = TextFormatter(IntegerStringConverter(), 0, integerFilter)

        val sizeOfBrushLayout = HBox(sizeOfBrushTextInput, sizeOfBrushTextInputLabel)
        sizeOfBrushLayout.alignment = Pos.CENTER_LEFT
        sizeOfBrushTextInput.prefColumnCount = 2
        //
        val hideCheckBox = CheckBox("Спрятать приложение")
        //
        val timingSliderText = Text("Задержка")
        val timingSlider = Slider(0.0, 10.0, 0.0)
        timingSlider.isShowTickMarks = true
        timingSlider.isShowTickLabels = true
        val timingSliderLayout = VBox(timingSliderText, timingSlider)
        //
        val screenshotButton = Button("Screenshoot")
        //
        val imageCanvas = Canvas(1.0, 1.0)
        val drawCanvas = Canvas()
        val cropCanvas = Canvas()
        val canvasLayout = StackPane(
            imageCanvas,
            drawCanvas,
            cropCanvas
        )
        canvasLayout.alignment = Pos.TOP_LEFT
        //
        val controlBarLayout = HBox(
            screenshotButton,
            timingSliderLayout,
            hideCheckBox,
            sizeOfBrushLayout,
            colorOfBrushPicker,
            cropImageButton
        )
        controlBarLayout.spacing = 10.0
        controlBarLayout.alignment = Pos.CENTER_LEFT
        //
        val masterVBoxLayout = VBox(
            menuBar,
            controlBarLayout,
            canvasLayout
        )
        val scene = Scene(masterVBoxLayout, 1280.0, 720.0)

        // EventHandler's
        makeScreenshotMenuItem.accelerator = KeyCodeCombination(KeyCode.H, KeyCombination.CONTROL_DOWN)
        makeScreenshotMenuItem.onAction = EventHandler {
            this.makeScreenshot(canvasLayout, primaryStage, hideCheckBox, timingSlider, masterVBoxLayout)
        }
        openImageMenuItem.accelerator = KeyCodeCombination(KeyCode.O, KeyCombination.CONTROL_DOWN)
        openImageMenuItem.onAction = EventHandler {
            clearCanvasLayoutAndSetImage(canvasLayout, openImage(primaryStage))
            resizeToSizeOfApp(
                canvasLayout,
                masterVBoxLayout.width,
                masterVBoxLayout.height - menuBar.height - controlBarLayout.height
            )
        }
        defaultSaveImageMenuItem.accelerator = KeyCodeCombination(KeyCode.F, KeyCombination.CONTROL_DOWN)
        defaultSaveImageMenuItem.onAction = EventHandler {
            saveImage(true, canvasLayout, primaryStage)
        }
        saveImageMenuItem.accelerator = KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN)
        saveImageMenuItem.onAction = EventHandler {
            saveImage(false, canvasLayout, primaryStage)
        }
        exitImageMenuItem.accelerator = KeyCodeCombination(KeyCode.C, KeyCombination.CONTROL_DOWN)
        exitImageMenuItem.onAction = EventHandler {
            clearImage(canvasLayout)
        }
        sizeOfBrushTextInput.textProperty().addListener(fun(_: Observable) {
            if (sizeOfBrushTextInput.text != "")
                this.brushSize = sizeOfBrushTextInput.text.toInt()
            else
                this.brushSize = 0
        })
        cropImageButton.onAction = EventHandler {
            this.isCropping = true
        }
        canvasLayout.onMouseReleased = EventHandler {
            if (this.isCropping) {
                this.isCropping = false
                cropImage(canvasLayout, masterVBoxLayout)
            }
        }
        canvasLayout.onMouseDragged = EventHandler { evt ->
            if (!this.isCropping) {
                if (evt.button == MouseButton.PRIMARY) {
                    drawCircle(canvasLayout, this.brushSize, colorOfBrushPicker.value, evt.x, evt.y)
                }
                if (evt.button == MouseButton.SECONDARY) {
                    clearRect(canvasLayout, this.brushSize, evt.x, evt.y)
                }
            } else {
                lastX = evt.x
                lastY = evt.y
                cropCanvas.graphicsContext2D.clearRect(0.0, 0.0, cropCanvas.width, cropCanvas.height)
                cropCanvas.graphicsContext2D.strokeRect(
                    if (firstX < lastX) firstX else lastX,
                    if (firstY < lastY) firstY else lastY,
                    abs(firstX - lastX),
                    abs(firstY - lastY)
                )
            }
        }
        canvasLayout.onMousePressed = EventHandler { evt ->
            if (!this.isCropping) {
                if (evt.button == MouseButton.PRIMARY) {
                    drawCircle(canvasLayout, this.brushSize, colorOfBrushPicker.value, evt.x, evt.y)
                }
                if (evt.button == MouseButton.SECONDARY) {
                    clearRect(canvasLayout, this.brushSize, evt.x, evt.y)
                }
            } else {
                this.firstX = evt.x
                this.firstY = evt.y
            }
        }
        screenshotButton.onAction = EventHandler {
            this.makeScreenshot(canvasLayout, primaryStage, hideCheckBox, timingSlider, masterVBoxLayout)
        }
        primaryStage.widthProperty().addListener(fun(evt: Observable) {
            evt.toString()
            resizeToSizeOfApp(
                canvasLayout,
                masterVBoxLayout.width,
                masterVBoxLayout.height - menuBar.height - controlBarLayout.height
            )
        })
        primaryStage.heightProperty().addListener(fun(evt: Observable) {
            evt.toString()
            resizeToSizeOfApp(
                canvasLayout,
                masterVBoxLayout.width,
                masterVBoxLayout.height - menuBar.height - controlBarLayout.height
            )
        })
        //

        primaryStage.minWidth = 800.0
        primaryStage.scene = scene
        primaryStage.show()
    }

    private fun openImage(primaryStage: Stage): WritableImage? {
        val fileChooser = FileChooser()
        fileChooser.extensionFilters.add(FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg"))
        val file = fileChooser.showOpenDialog(primaryStage)
        return toFXImage(ImageIO.read(file), null)
    }

    private fun saveImage(useDefault: Boolean, canvasLayout: StackPane, primaryStage: Stage) {
        val file: File
        if (useDefault) {
            file = File(System.getenv("USERPROFILE") + "\\Documents\\Screenshot" + ".png")
        } else {
            val fileChooser = FileChooser()
            fileChooser.extensionFilters.add(FileChooser.ExtensionFilter("Image Files", "*.png"))
            fileChooser.initialDirectory = File(getLastDirectory())
            file = fileChooser.showSaveDialog(primaryStage)
        }
        val params = SnapshotParameters()
        params.fill = Color.TRANSPARENT
        (canvasLayout.children[0] as Canvas).graphicsContext2D.drawImage(
            (canvasLayout.children[1] as Canvas).snapshot(
                params,
                null
            ), 0.0, 0.0
        )
        val image = (canvasLayout.children[0] as Canvas).snapshot(params, null)
        clearImage(canvasLayout)
        ImageIO.write(fromFXImage(image, null), "png", file)
        if (!useDefault) {
            setLastDirectory(file.path)
        }
    }

    private fun setLastDirectory(text: String) {
        try {
            BufferedWriter(PrintWriter(System.getenv("USERPROFILE") + "\\Documents\\ScreenshotShooterConfig.txt")).use { bw ->
                val file = File(text)
                if (file.isFile)
                    bw.write(file.parent)
                else
                    bw.write(text)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun getLastDirectory(): String {
        try {
            BufferedReader(FileReader(System.getenv("USERPROFILE") + "\\Documents\\ScreenshotShooterConfig.txt")).use { reader ->
                return reader.readLine()
            }
        } catch (e: IOException) {
//            e.printStackTrace()
            return System.getenv("USERPROFILE") + "\\Documents\\"
        }
    }

    private fun clearImage(canvasLayout: StackPane) {
        (canvasLayout.children[0] as Canvas).width = 0.0
        (canvasLayout.children[0] as Canvas).height = 0.0
        (canvasLayout.children[1] as Canvas).width = 0.0
        (canvasLayout.children[1] as Canvas).height = 0.0
        (canvasLayout.children[2] as Canvas).width = 0.0
        (canvasLayout.children[0] as Canvas).height = 0.0
    }

    private fun clearRect(canvasLayout: StackPane, brushSize: Int, x: Double, y: Double) {
        val drawCanvas = canvasLayout.children[1] as Canvas
        drawCanvas.graphicsContext2D.clearRect(
            x - brushSize / 2,
            y - brushSize / 2,
            brushSize.toDouble(),
            brushSize.toDouble()
        )
    }

    private fun drawCircle(canvasLayout: StackPane, brushSize: Int, color: Color, x: Double, y: Double) {
        val drawCanvas = canvasLayout.children[1] as Canvas
        drawCanvas.graphicsContext2D.fill = color
        drawCanvas.graphicsContext2D.fillOval(
            x - brushSize / 2,
            y - brushSize / 2,
            brushSize.toDouble(),
            brushSize.toDouble()
        )
    }

    private fun resizeToSizeOfApp(canvasLayout: StackPane, width: Double, height: Double) {
        val coefX = min(width / (canvasLayout.children[0] as Canvas).width, height / canvasLayout.height)
        canvasLayout.transforms.setAll(Scale(coefX, coefX))
    }

    private fun makeScreenshot(
        canvasLayout: StackPane,
        primaryStage: Stage,
        hideCheckBox: CheckBox,
        timingSlider: Slider,
        masterVBoxLayout: VBox
    ) {
        if (hideCheckBox.isSelected) {
            primaryStage.isIconified = true
        }
        Timer().schedule((timingSlider.value * 1000 + (if (hideCheckBox.isSelected) 200 else 0)).toLong()) {
            Platform.runLater {
                clearCanvasLayoutAndSetImage(canvasLayout, getScreenshotImage())
                resizeToSizeOfApp(
                    canvasLayout,
                    masterVBoxLayout.width,
                    masterVBoxLayout.height - (masterVBoxLayout.children[0] as MenuBar).height - (masterVBoxLayout.children[1] as HBox).height
                )
                if (hideCheckBox.isSelected) {
                    primaryStage.isIconified = false
                }
            }

        }
    }

    private fun cropImage(canvasLayout: StackPane, masterVBoxLayout: VBox) {
        val canvases = canvasLayout.children
        val params = SnapshotParameters()
        params.fill = Color.TRANSPARENT
        val drawTmp = WritableImage(
            (canvases[1] as Canvas).snapshot(params, null).pixelReader,
            max(min(firstX, lastX), 0.0).toInt(),
            max(min(firstY, lastY), 0.0).toInt(),
            min(
                abs(lastX - firstX),
                (canvases[1] as Canvas).width - max(min(firstX, lastX), 0.0).toInt()
            ).toInt(),
            min(
                abs(lastY - firstY),
                (canvases[1] as Canvas).height - max(min(firstY, lastY), 0.0).toInt()
            ).toInt()
        )
        clearCanvasLayoutAndSetImage(
            canvasLayout, WritableImage(
                (canvases[0] as Canvas).snapshot(params, null).pixelReader,
                max(min(firstX, lastX), 0.0).toInt(),
                max(min(firstY, lastY), 0.0).toInt(),
                min(
                    abs(lastX - firstX),
                    (canvases[0] as Canvas).width - max(min(firstX, lastX), 0.0).toInt()
                ).toInt(),
                min(
                    abs(lastY - firstY),
                    (canvases[0] as Canvas).height - max(min(firstY, lastY), 0.0).toInt()
                ).toInt()
            )
        )
        (canvases[1] as Canvas).graphicsContext2D.drawImage(drawTmp, 0.0, 0.0)
        resizeToSizeOfApp(
            canvasLayout,
            masterVBoxLayout.width,
            masterVBoxLayout.height - (masterVBoxLayout.children[0] as MenuBar).height - (masterVBoxLayout.children[1] as HBox).height
        )
    }

    private fun clearCanvasLayoutAndSetImage(canvasLayout: StackPane, wImage: WritableImage?) {
        if (wImage != null) {
            val canvases = canvasLayout.children
            canvasLayout.resize(wImage.width, wImage.height)
            (canvases[0] as Canvas).width = wImage.width
            (canvases[0] as Canvas).height = wImage.height
            (canvases[1] as Canvas).width = wImage.width
            (canvases[1] as Canvas).height = wImage.height
            (canvases[2] as Canvas).width = wImage.width
            (canvases[2] as Canvas).height = wImage.height
            (canvases[0] as Canvas).graphicsContext2D.clearRect(0.0, 0.0, wImage.width, wImage.height)
            (canvases[1] as Canvas).graphicsContext2D.clearRect(0.0, 0.0, wImage.width, wImage.height)
            (canvases[2] as Canvas).graphicsContext2D.clearRect(0.0, 0.0, wImage.width, wImage.height)
            (canvases[0] as Canvas).graphicsContext2D.drawImage(wImage, 0.0, 0.0)
        }
    }

    private fun getScreenshotImage(): WritableImage? {
        val robot = Robot()
        val screenSize = Toolkit.getDefaultToolkit().screenSize
        val captureRect = Rectangle(0, 0, screenSize.width, screenSize.height)
        return toFXImage(robot.createScreenCapture(captureRect), null)
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            launch(ScreenshotShooter::class.java)
        }
    }
}
