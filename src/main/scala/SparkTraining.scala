import com.typesafe.config.Config
import org.apache.hadoop.fs.{FileSystem, Path}
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork
import org.deeplearning4j.optimize.listeners.ScoreIterationListener
import org.deeplearning4j.spark.impl.multilayer.SparkDl4jMultiLayer
import org.deeplearning4j.spark.impl.paramavg.ParameterAveragingTrainingMaster
import org.deeplearning4j.ui.api.UIServer
import org.deeplearning4j.ui.model.stats.StatsListener
import org.deeplearning4j.ui.model.storage.FileStatsStorage
import org.deeplearning4j.util.ModelSerializer
import org.nd4j.linalg.dataset.DataSet
import utils.AppLogger

import java.io.{BufferedOutputStream, File}
import java.util.Collections


object SparkTraining {
  private val logger = AppLogger("SparkTraining")

  def train(sc: SparkSession, model : MultiLayerNetwork, dataset: RDD[DataSet], conf: Config, outputPath : String): Unit = {

//    val statsStorage = new FileStatsStorage(new File("myNetworkTrainingStats.dl4j"))  //If file already exists: load the data from it
//    val uiServer = UIServer.getInstance()
//    uiServer.attach(statsStorage)

    logger.warn("Spark Distributed Training")
    val batchSizePerWorker = conf.getInt("app.trainingParam.batchSizePerWorker")
    val averageFrequency = conf.getInt("app.trainingParam.averagingFrequency")
    val trainingMaster = new ParameterAveragingTrainingMaster.Builder(batchSizePerWorker)
      .averagingFrequency(averageFrequency)
      .batchSizePerWorker(batchSizePerWorker)
      .workerPrefetchNumBatches(batchSizePerWorker)
      .build()
    logger.warn("Define spark model")
    //Create the SparkDl4jMultiLayer instance
    val sparkNet : SparkDl4jMultiLayer = new SparkDl4jMultiLayer(sc.sparkContext, model, trainingMaster)

    val ss = new FileStatsStorage(new File("myNetworkTrainingStats.dl4j"))
    sparkNet.setListeners(ss, new ScoreIterationListener(1))
    sparkNet.setListeners(ss, Collections.singletonList(new StatsListener(null)))

    val numEpochs = conf.getInt("app.trainingParam.numEpochs") // Number of epochs to train

    // Train the model using the DataSet

    logger.info("Training")
    (0 until numEpochs).map( epoch => {
      dataset.persist()
      val startTime = System.currentTimeMillis()
      logger.warn("Start training epoch " + epoch)
      //sample with path String training instead of fitting all dataset
//      sparkNet.fit("hdfs://localhost:9000/user/manh/WikiText")
      sparkNet.fit(dataset)
      logger.warn(s"Learning rate: ${sparkNet.getNetwork.getLearningRate(0)}")
      // After the epoch ends
      val endTime = System.currentTimeMillis()
      logger.warn(s"Epoch time: ${endTime - startTime}ms")

    })
    saveModel(sc, sparkNet, outputPath + "/outputModel.bin")
    sc.stop()

  }

  def saveModel(sc: SparkSession, model: SparkDl4jMultiLayer, outputPath: String): Unit = {
    logger.warn("Start saving model ")
    val fileSystem : FileSystem = FileSystem.get(new java.net.URI(outputPath), sc.sparkContext.hadoopConfiguration)
    val net : MultiLayerNetwork = model.getNetwork
    val outputStream = new BufferedOutputStream(fileSystem.create(new Path(outputPath)))
    try {
      ModelSerializer.writeModel(net, outputStream, true)
      logger.warn("Saving model succeed")
    } finally {
      outputStream.close()
    }
  }

}
