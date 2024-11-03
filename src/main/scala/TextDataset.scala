


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

import org.apache.hadoop.fs.{FileSystem, Path}
import org.apache.spark.sql.SparkSession

import java.io.BufferedOutputStream
import scala.util.Using

/*
TextDataset: Class for any dataset for the task
dataName : name of the dataset
dataLocation: list of string contains list of file of the dataset. e.g: /home/wikiText/wiki_train.txt. Useful when working with nlp dataset
outLocation: output Path to save to computational dataset, could be HDFS based or s3 or local
 */

class TextDataset(val dataName : String, val dataLocation : List[String]) {

}

object TextDataset {
  private val logger = AppLogger("TextDataset")

  // Load dataset from config
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

  // Load dataset using normal File API
  def loadData(textDataset: TextDataset): List[String] = {
    val totalLines = getTotalLineCount(textDataset)
    logger.warn(s"Total lines in dataset: $totalLines")

    logger.warn("Start loading data ..")

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

  // Load dataset using SparkAPI
  def loadDataSpark(inputPath : String, sc : SparkSession, windowSize: Int): RDD[DataSet] = {
    logger.warn(s"Load and computing dataset from : " + inputPath)
    logger.warn(s"Window Size : " + windowSize)
    // Filter sentences that longer than 20 character and more than 5 words
    val distData = sc.sparkContext.textFile(inputPath).filter(_.length>20).filter(_.split(" ").length>5)
    distData.flatMap(sentence => {
      createSlidingWindowWithPositionalEmbedding(sentence.split(" ").toList, windowSize)
    })
  }

  // Way to load and save data to file instead of computing it everytime
  def loadAndSaveData(inputPath: String, sc: SparkSession, conf: Config): String = {
//     Other way to load data from local
    val dataset = TextDataset.loadData(TextDataset.fromConfig(conf))
    val sentences = TextDataset.preprocessData(dataset)
    val distData = sc.sparkContext.parallelize(sentences)

    val slidingWindowDataset = distData.flatMap(
      sentence => createSlidingWindowWithPositionalEmbedding(sentence.split(" ").toList, conf.getInt("app.trainingParam.windowSize")
    ).iterator)

//     Save dataset to files
    logger.warn("Start computing dataset and when finish save it to files")
    TextDataset.saveToFiles(slidingWindowDataset, conf.getConfig("app.dataset"), sc.sparkContext)
  }

  def preprocessData(textDataset: List[String]): List[String] =
    // Filter sentences that longer than 20 character and more than 5 words
    textDataset.filter(_.length>20).filter(_.split(" ").length>5)

  def saveToFiles(textDataset: RDD[DataSet], conf: Config, sc : SparkContext) : String = {

    val numberOfRDDs = conf.getInt("numberOfRDDs")

    val lengthOfRDDs = (textDataset.count() / numberOfRDDs).toInt
    val dataSetIterator : DataSetIterator = new ListDataSetIterator(textDataset.collect().toList.asJava, lengthOfRDDs)

    logger.warn("Try saving it to HDFS Object File")
    val hdfs = conf.getString("HDFS")
    val fileSystem = FileSystem.get(new java.net.URI(hdfs), sc.hadoopConfiguration)
    val hdfsDirectoryPath = conf.getString("HDFSDirectory")

    (0 until 10).foreach( count => {
      if (dataSetIterator.hasNext) {
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
        logger.info("Data process " + count)
      }
    })

//    logger.info("Try saving it to HDFS Files")
//    val hdfsPath = conf.getString("HDFSFile")
//    textDataset.saveAsTextFile(hdfsPath)
    s"$hdfs/$hdfsDirectoryPath"
  }

  def loadFromFiles(sc: SparkContext, conf: Config) : RDD[DataSet] = {
    logger.info("Try loading DataSet from HDFS Object File")
    val hdfsObjectPath = conf.getString("HDFSDirectory")
    val slwDataset = sc.objectFile[DataSet](hdfsObjectPath)

    //    val res = SparkDataValidation.validateDataSets(sc.sparkContext, hdfsObjectPath)
    //    logger.error(" " + res.toString)
    slwDataset
  }


}

