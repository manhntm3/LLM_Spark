
import com.typesafe.config.{Config, ConfigFactory}
import data.SlidingWindowWithPositionalEmbedding.createSlidingWindowWithPositionalEmbedding
import model.TransformerModel.testModel
import model.{ModelParam, TransformerModel}
import org.apache.hadoop.fs.{FileSystem, Path}
import org.apache.spark.storage.StorageLevel
import org.apache.spark.{SparkConf, SparkContext}
import org.deeplearning4j.core.storage.StatsStorage
import org.deeplearning4j.datasets.iterator.utilty.ListDataSetIterator
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork
import org.deeplearning4j.optimize.api.InvocationType
import org.deeplearning4j.optimize.listeners.{EvaluativeListener, ScoreIterationListener}
import org.deeplearning4j.spark.api.{RDDTrainingApproach, TrainingMaster, TrainingWorker}
import org.deeplearning4j.spark.data.BatchAndExportDataSetsFunction
import org.deeplearning4j.spark.impl.multilayer.SparkDl4jMultiLayer
import org.deeplearning4j.spark.impl.paramavg.{BaseTrainingResult, BaseTrainingWorker, ParameterAveragingTrainingMaster}
import org.deeplearning4j.spark.parameterserver.training.SharedTrainingMaster
import org.deeplearning4j.spark.util.data.SparkDataValidation
import org.deeplearning4j.ui.api.UIServer
import org.deeplearning4j.ui.model.stats.StatsListener
import org.deeplearning4j.ui.model.storage.{FileStatsStorage, InMemoryStatsStorage}
import org.nd4j.linalg.dataset.DataSet
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator
import org.nd4j.parameterserver.distributed.conf.VoidConfiguration
import org.nd4j.parameterserver.distributed.v2.enums.MeshBuildMode
import utils.{AppConfig, AppLogger, AppSpark}

import java.io.{BufferedOutputStream, File, OutputStream}
import java.util.Collections
import scala.collection.JavaConverters._

object SparkAssigment {

  def test1(): Unit = {
    // specify the log
    val logger = AppLogger("SparkAssignment")

    logger.error("Start loading config")
    // Start load config
    val conf = AppConfig()
    logger.error("Load config success")
    // Start load data
    val dataset = TextDataset.fromConfig(conf)

    logger.error("Load Spark Config")
    val sc = AppSpark.createSparkSession("Sliding Window Dataset with Position Embedding", true)

    val sentences = TextDataset.preprocessData(TextDataset.loadData(dataset))
    logger.error("Start parallelize")
    val distData = sc.sparkContext.parallelize(sentences)

    val slidingWindowDataset = distData.flatMap(
      sentence => createSlidingWindowWithPositionalEmbedding(sentence.split(" ").toList, conf.getInt("app.trainingParam.windowSize")
      ).iterator)

    // Save dataset to files
    logger.error("Start computing dataset and when finish save it to files")
    val datasetPath = TextDataset.saveToFiles(slidingWindowDataset, conf.getConfig("app.dataset"), sc.sparkContext)

    logger.error("Define Model")
    val model : MultiLayerNetwork = TransformerModel.fromParam(new ModelParam())  // Input size, hidden size, ou
    // Load or create a model (assuming the model has been defined, e.g., TransformerModel.createModel())
    model.init()
    //    testModel(model)

    //    val statsStorage = new FileStatsStorage(new File("myNetworkTrainingStats.dl4j"))  //If file already exists: load the data from it
    //    val uiServer = UIServer.getInstance()
    //    uiServer.attach(statsStorage)

    logger.error("Spark Distributed Training")
    val batchSizePerWorker = 8
    val trainingMaster = new ParameterAveragingTrainingMaster.Builder(batchSizePerWorker)
      .averagingFrequency(5)
      .batchSizePerWorker(batchSizePerWorker)
      .workerPrefetchNumBatches(8)
      .build()
    logger.error("Define spark model")
    //Create the SparkDl4jMultiLayer instance
    val sparkNet : SparkDl4jMultiLayer = new SparkDl4jMultiLayer(sc.sparkContext, model, trainingMaster)

    //    model.setListeners(new ScoreIterationListener(10))
    val ss = new FileStatsStorage(new File("myNetworkTrainingStats.dl4j"))
    sparkNet.setListeners(ss, new ScoreIterationListener(1))
    sparkNet.setListeners(ss, Collections.singletonList(new StatsListener(null)))


    // Train the model using the DataSetIterator
    val numEpochs = 40 // Number of epochs to train

    logger.error("Training")
    (0 until numEpochs).foreach( epoch => {
      logger.error("Start training epoch " + epoch)
      sparkNet.fit(datasetPath)
      //      logger.error("Completed training epoch " + epoch)
    })

    sc.stop()

    //    model.save("")

    logger.error("Training complete.")
  }

  def main(args: Array[String]): Unit = {
    // specify the log
    val logger = AppLogger("SparkAssignment")

    if (args.length < 2) {
      logger.error("Cannot find the input and output argument!")
      return
    }
    logger.error("Start loading config")
    // Start load config
    val conf = AppConfig()
    logger.error("Load config success")

    logger.error("Load Spark Config")
    val sc = AppSpark.createSparkSession("Sliding Window Dataset with Position Embedding", true)

    // Start load data
    logger.error("Start load and parallelize data")
    val slidingWindowDataset = TextDataset.loadDataSpark(args(0), sc, conf.getInt("app.trainingParam.windowSize"))
//    val sentences = TextDataset.preprocessData(dataset)
//    val distData = sc.sparkContext.parallelize(sentences)

//    val slidingWindowDataset = distData.flatMap(
//        sentence => createSlidingWindowWithPositionalEmbedding(sentence.split(" ").toList, conf.getInt("app.trainingParam.windowSize")
//      ).iterator)

    // Save dataset to files
    logger.error("Start computing dataset and when finish save it to files")
//    val datasetPath = TextDataset.saveToFiles(slidingWindowDataset, conf.getConfig("app.dataset"), sc.sparkContext)

    logger.error("Define Model")
    val model : MultiLayerNetwork = TransformerModel.fromParam(new ModelParam())  // Input size, hidden size, ou
    // Load or create a model (assuming the model has been defined, e.g., TransformerModel.createModel())
    model.init()
//    testModel(model)

//    val statsStorage = new FileStatsStorage(new File("myNetworkTrainingStats.dl4j"))  //If file already exists: load the data from it
//    val uiServer = UIServer.getInstance()
//    uiServer.attach(statsStorage)

    logger.error("Spark Distributed Training")
    val batchSizePerWorker = 8
    val trainingMaster = new ParameterAveragingTrainingMaster.Builder(batchSizePerWorker)
      .averagingFrequency(5)
      .batchSizePerWorker(batchSizePerWorker)
      .workerPrefetchNumBatches(8)
      .build()
    logger.error("Define spark model")
    //Create the SparkDl4jMultiLayer instance
    val sparkNet : SparkDl4jMultiLayer = new SparkDl4jMultiLayer(sc.sparkContext, model, trainingMaster)

//    model.setListeners(new ScoreIterationListener(10))
    val ss = new FileStatsStorage(new File("myNetworkTrainingStats.dl4j"))
    sparkNet.setListeners(ss, new ScoreIterationListener(1))
    sparkNet.setListeners(ss, Collections.singletonList(new StatsListener(null)))


    // Train the model using the DataSetIterator
    val numEpochs = 5 // Number of epochs to train

    logger.error("Training")
    (0 until numEpochs).foreach( epoch => {
      logger.error("Start training epoch " + epoch)
      sparkNet.fit(slidingWindowDataset)
//      logger.error("Completed training epoch " + epoch)
    })

    sc.stop()

//    model.save("")

    logger.error("Training complete.")
  }
}
    // main function

  
