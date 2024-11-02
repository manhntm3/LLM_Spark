
import org.scalatest.funsuite.AnyFunSuite

import java.nio.file.{Files, Path, Paths}
import scala.io.Source
import java.nio.charset.StandardCharsets
import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.should.Matchers

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Using}
import org.slf4j.{Logger, LoggerFactory}

// Mock AppLogger for testing purposes
object AppLogger {
  def apply(name: String): Logger = LoggerFactory.getLogger(name)
}

// Assuming the TextDataset and TextDatasetFactory classes are accessible
class TextDatasetTest extends AnyFunSuite  with Matchers {

  object AppLogger {
    def apply(name: String): AppLogger = new AppLogger(name)
  }

  class AppLogger(name: String) {
    def info(message: String): Unit = println(s"INFO [$name]: $message")
    def error(message: String): Unit = println(s"ERROR [$name]: $message")
  }

  test("TextDataset.fromConfig creates a TextDataset with the correct dataName and dataLocation when trainFilename is specified") {
    val configString =
      """
        |app.dataset.name = "testDataset"
        |app.dataset.inputDirectory = "/path/to/input"
        |app.dataset.trainFilename = "train.txt"
      """.stripMargin
    val conf = ConfigFactory.parseString(configString)

    val textDataset = TextDataset.fromConfig(conf)

    textDataset.dataName shouldBe "testDataset"
    textDataset.dataLocation shouldBe List("/path/to/input/train.txt")
  }

  test("TextDataset.fromConfig lists files from inputDirectory when trainFilename is not specified") {
    val tempDir = Files.createTempDirectory("testInputDir")
    val file1 = Files.createTempFile(tempDir, "file1", ".txt")
    val file2 = Files.createTempFile(tempDir, "file2", ".txt")

    val configString =
      s"""
         |app.dataset.name = "testDataset"
         |app.dataset.inputDirectory = "${tempDir.toString}"
       """.stripMargin
    val conf = ConfigFactory.parseString(configString)

    val textDataset = TextDataset.fromConfig(conf)

    textDataset.dataName shouldBe "testDataset"
    val expectedPaths = List(file1.toString, file2.toString)
    textDataset.dataLocation should contain theSameElementsAs expectedPaths
  }

  test("TextDataset.fromConfig throws an exception when inputDirectory does not exist") {
    val nonExistingDir = "/non/existing/directory"

    val configString =
      s"""
         |app.dataset.name = "testDataset"
         |app.dataset.inputDirectory = "$nonExistingDir"
       """.stripMargin
    val conf = ConfigFactory.parseString(configString)

    val exception = intercept[Exception] {
      TextDataset.fromConfig(conf)
    }

    exception shouldBe a[java.nio.file.NoSuchFileException]
  }

  test("TextDataset.createDataset creates a TextDataset with given dataName and dataLocation") {
    val dataName = "testDataset"
    val dataLocation = List("/path/to/file1.txt", "/path/to/file2.txt")

    val textDataset = TextDataset.createDataset(dataName, dataLocation)

    textDataset.dataName shouldBe dataName
    textDataset.dataLocation shouldBe dataLocation
  }

  test("TextDataset.getTotalLineCount returns correct line count for valid files") {
    val tempDir = Files.createTempDirectory("testGetTotalLineCount")
    val file1 = tempDir.resolve("file1.txt")
    val file2 = tempDir.resolve("file2.txt")

    Files.write(file1, "Line1\nLine2\nLine3".getBytes(StandardCharsets.UTF_8))
    Files.write(file2, "LineA\nLineB".getBytes(StandardCharsets.UTF_8))

    val dataLocation = List(file1.toString, file2.toString)
    val textDataset = new TextDataset("testDataset", dataLocation)

    val totalLineCount = TextDataset.getTotalLineCount(textDataset)

    totalLineCount shouldBe 5L
  }

  test("TextDataset.getTotalLineCount skips files that do not exist and counts lines in existing files") {
    val tempDir = Files.createTempDirectory("testGetTotalLineCount")
    val file1 = tempDir.resolve("file1.txt")
    val nonExistingFile = tempDir.resolve("nonExisting.txt")

    Files.write(file1, "Line1\nLine2\nLine3".getBytes(StandardCharsets.UTF_8))

    val dataLocation = List(file1.toString, nonExistingFile.toString)
    val textDataset = new TextDataset("testDataset", dataLocation)

    val totalLineCount = TextDataset.getTotalLineCount(textDataset)

    totalLineCount shouldBe 3L
  }

  test("TextDataset.getTotalLineCount returns 0 when dataLocation is empty") {
    val textDataset = new TextDataset("testDataset", List.empty)

    val totalLineCount = TextDataset.getTotalLineCount(textDataset)

    totalLineCount shouldBe 0L
  }

  test("TextDataset.loadData loads all lines from files in dataLocation") {
    val tempDir = Files.createTempDirectory("testLoadData")
    val file1 = tempDir.resolve("file1.txt")
    val file2 = tempDir.resolve("file2.txt")

    Files.write(file1, "Line1\nLine2\nLine3".getBytes(StandardCharsets.UTF_8))
    Files.write(file2, "LineA\nLineB".getBytes(StandardCharsets.UTF_8))

    val dataLocation = List(file1.toString, file2.toString)
    val textDataset = new TextDataset("testDataset", dataLocation)

    val data = TextDataset.loadData(textDataset)

    data should contain theSameElementsInOrderAs List("Line1", "Line2", "Line3", "LineA", "LineB")
  }

  test("TextDataset.loadData skips non-existing files and loads data from existing files") {
    val tempDir = Files.createTempDirectory("testLoadData")
    val file1 = tempDir.resolve("file1.txt")
    val nonExistingFile = tempDir.resolve("nonExisting.txt")

    Files.write(file1, "Line1\nLine2\nLine3".getBytes(StandardCharsets.UTF_8))

    val dataLocation = List(file1.toString, nonExistingFile.toString)
    val textDataset = new TextDataset("testDataset", dataLocation)

    val data = TextDataset.loadData(textDataset)

    data should contain theSameElementsInOrderAs List("Line1", "Line2", "Line3")
  }

  test("TextDataset.loadData returns empty list when dataLocation is empty") {
    val textDataset = new TextDataset("testDataset", List.empty)

    val data = TextDataset.loadData(textDataset)

    data shouldBe empty
  }

  test("TextDataset.preprocessData filters sentences longer than 20 characters and with more than 5 words") {
    val sentences = List(
      "Short sentence.",
      "Exactly twenty char",
      "This is a longer sentence with more than five words.",
      "Another long sentence that should be included in the result.",
      "This one is too short.",
      "This is short."
    )

    val processed = TextDataset.preprocessData(sentences)

    processed should contain theSameElementsAs List(
      "This is a longer sentence with more than five words.",
      "Another long sentence that should be included in the result."
    )
  }


}