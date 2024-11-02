
import org.scalatest.funsuite.AnyFunSuite

import java.nio.file.{Files, Paths, Path}
import scala.io.Source
import java.nio.charset.StandardCharsets
import com.typesafe.config.ConfigFactory
import scala.collection.JavaConverters._
import scala.util.{Using, Success, Failure}
import org.slf4j.{Logger, LoggerFactory}

// Mock AppLogger for testing purposes
object AppLogger {
  def apply(name: String): Logger = LoggerFactory.getLogger(name)
}

// Assuming the TextDataset and TextDatasetFactory classes are accessible
class TextDatasetTest extends AnyFunSuite {

  test("splitIntoShards should correctly split input files into shards") {
    // Setup: Create temporary directories for input and output
    val tempInputDir = Files.createTempDirectory("textdataset_input")
    val tempOutputDir = Files.createTempDirectory("textdataset_output")

    try {
      // Create sample input files with known content
      val inputFile1 = tempInputDir.resolve("file1.txt")
      val inputFile2 = tempInputDir.resolve("file2.txt")

      val content1 = "Line1\nLine2\nLine3\nLine4\nLine5"
      val content2 = "Line6\nLine7\nLine8\nLine9\nLine10"

      Files.write(inputFile1, content1.getBytes(StandardCharsets.UTF_8))
      Files.write(inputFile2, content2.getBytes(StandardCharsets.UTF_8))

      // Prepare the Config object
      val configStr = s"""
        app.dataset {
          name = "test_dataset"
          inputDirectory = "${tempInputDir.toAbsolutePath}"
          outputDirectory = "${tempOutputDir.toAbsolutePath}"
          numberOfShards = 2
        }
      """
      val conf = ConfigFactory.parseString(configStr)

      // Instantiate TextDataset using the factory
      val textDataset = TextDatasetFactory.fromConfig(conf)

      // Run the method under test
      textDataset.splitIntoShards()

      // Verify that the shards are created correctly
      val shardFiles = Files.list(tempOutputDir).iterator().asScala.toList
      assert(shardFiles.size == 2, "Expected 2 shard files")

      val shardContents = shardFiles.sortBy(_.getFileName.toString).map { path =>
        Source.fromFile(path.toFile).getLines().toList
      }

      // Expected lines per shard
      val expectedLinesPerShard = List(
        List("Line6", "Line7", "Line8", "Line9", "Line10"),
        List("Line1", "Line2", "Line3", "Line4", "Line5")
      )

      assert(shardContents == expectedLinesPerShard, "Shard contents do not match expected lines")

    } finally {
      // Clean up temporary directories and files
      def deleteRecursively(path: Path): Unit = {
        if (Files.exists(path)) {
          if (Files.isDirectory(path)) {
            Files.list(path).iterator().asScala.foreach(deleteRecursively)
          }
          Files.deleteIfExists(path)
        }
      }

      deleteRecursively(tempInputDir)
      deleteRecursively(tempOutputDir)
    }
  }
}