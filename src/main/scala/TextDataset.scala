

import com.typesafe.config.ConfigFactory
import com.typesafe.config.Config
import data.SlidingWindowWithPositionalEmbedding.createSlidingWindowWithPositionalEmbedding
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.deeplearning4j.datasets.iterator.utilty.ListDataSetIterator
import org.nd4j.linalg.dataset.DataSet
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator

import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._
import scala.io.Source
import utils.{AppConfig, AppLogger}

import java.io.File
import org.apache.hadoop.fs.{FileSystem, Path}
import org.apache.spark.sql.SparkSession

import java.io.BufferedOutputStream
import scala.util.Using

/*
TextDataset: Class for any dataset for the task
dataName : name of the dataset
dataLocation: list of string contains list of file of the dataset. e.g: /home/wikiText/wiki_train.txt. Useful when working with nlp dataset
outLocation: output path that will contain the shards when splitting into shards
numberOfShards: number of shards 
 */

class TextDataset(val dataName : String, val dataLocation : List[String]) {

}

object TextDataset {
  private val logger = AppLogger("TextDataset")

  def fromConfig(conf: Config): TextDataset = {
    val dataName = conf.getString("app.dataset.name")
    val inLocation: String = conf.getString("app.dataset.inputDirectory")
    val dataPaths : List[String] = {
      if (conf.hasPath("app.dataset.trainFilename")) {
        List(inLocation + "/" + conf.getString("app.dataset.trainFilename"))
      } else {
        Files.list(Paths.get(inLocation))     // Ensure `inLocation` is converted to a `Path`
          .iterator()
          .asScala
          .map(_.toString)                    // Convert Path to String
          .toList
      }
    }
    new TextDataset(dataName, dataPaths)
  }

  def createDataset(dataName : String, dataLocation : List[String]) : TextDataset = {
    new TextDataset(dataName, dataLocation)
  }

  // Calculate total number of lines across all files without loading all content into memory
  def getTotalLineCount(textDataset: TextDataset) : Long = {
    textDataset.dataLocation.foldLeft(0L) { (count, fileLocation) =>
      val path = Paths.get(fileLocation)
      if (Files.exists(path)) {
        logger.info(s"Counting lines in: $fileLocation")
        Using(Source.fromFile(fileLocation)) { source =>
          count + source.getLines().size.toLong
        }.getOrElse {
          logger.info(s"Error reading data from: $fileLocation")
          count
        }
      } else {
        logger.info(s"File does not exist: $fileLocation")
        count
      }
    }
  }

  def loadData(textDataset: TextDataset): List[String] = {
    val totalLines = getTotalLineCount(textDataset)
    logger.info(s"Total lines in dataset: $totalLines")

    logger.info("Start loading data ..")

    textDataset.dataLocation.flatMap ( fileLocation => {
      val path = Paths.get(fileLocation)
      if (Files.exists(path)) {
        logger.info(s"Processing file: $fileLocation")
        Using(Source.fromFile(fileLocation)) { source =>
          source.getLines().toList
        }.getOrElse(List.empty)
      } else {
        logger.error(s"File does not exist: $fileLocation")
        List.empty
        }
      }
    )
  }

  def loadDataSpark(inputPath : String, sc : SparkSession, windowSize: Int): RDD[DataSet] = {
    logger.error(s"Load and computing dataset from : " + inputPath)
    logger.error(s"Window Size : " + windowSize)
    // Filter sentences that longer than 20 character and more than 5 words
    val distData = sc.sparkContext.textFile(inputPath).filter(_.length>20).filter(_.split(" ").length>5)
    distData.flatMap(sentence => {
      createSlidingWindowWithPositionalEmbedding(sentence.split(" ").toList, windowSize)
    })
  }

  def preprocessData(textDataset: List[String]): List[String] =
    // Filter sentences that longer than 20 character and more than 5 words
    textDataset.filter(_.length>20).filter(_.split(" ").length>5)

  def saveToFiles(textDataset: RDD[DataSet], conf: Config, sc : SparkContext) : String = {

    val numberOfRDDs = conf.getInt("numberOfRDDs")
    val localPath = conf.getString("localDirectory")

    val lengthOfRDDs = (textDataset.count() / numberOfRDDs).toInt
    val dataSetIterator : DataSetIterator = new ListDataSetIterator(textDataset.collect().toList.asJava, lengthOfRDDs)
//    logger.error("Try saving it to Local Files")
    // First try to save to local file
//    val rootDir = new File(localPath)
//    var count = 0
//
//    while (dataSetIterator.hasNext) {
//      val ds: DataSet = dataSetIterator.next()
//      val outFile = new File(rootDir, s"dataset_${count}.bin")
//      ds.save(outFile)
//      count = count + 1
//      logger.error("Data process " + count)
//    }

    logger.error("Try saving it to HDFS Object File")
    val hdfs = conf.getString("HDFS")
    val fileSystem = FileSystem.get(new java.net.URI(hdfs), sc.hadoopConfiguration)
    val hdfsDirectoryPath = conf.getString("HDFSDirectory")

    var count = 0

    while (dataSetIterator.hasNext) {
      val ds: DataSet = dataSetIterator.next()
      val filePath = s"$hdfs/$hdfsDirectoryPath/dataset_$count.bin"
      val path = new Path(filePath)
      // Use resource management with Scala's `try-with-resources` equivalent
      val outputStream = new BufferedOutputStream(fileSystem.create(path))
      try {
        ds.save(outputStream)
      } finally {
        outputStream.close()
      }
      count = count + 1
      logger.error("Data process " + count)
    }
//    logger.error("Try saving it to HDFS Object Files")
//    textDataset.saveAsObjectFile(hdfsObjectPath)

//    logger.error("Try saving it to HDFS Files")
//    val hdfsPath = conf.getString("HDFSFile")
//    textDataset.saveAsTextFile(hdfsPath)
    s"$hdfs/$hdfsDirectoryPath"
  }

  def loadFromFiles(sc: SparkContext, conf: Config) : RDD[DataSet] = {
    logger.error("Try loading DataSet from HDFS Object File")
    val hdfsObjectPath = conf.getString("HDFSDirectory")
    val slwDataset = sc.objectFile[DataSet](hdfsObjectPath)

    //    val res = SparkDataValidation.validateDataSets(sc.sparkContext, hdfsObjectPath)
    //    logger.error(" " + res.toString)
    slwDataset
  }


}

