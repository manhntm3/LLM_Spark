package model

import org.deeplearning4j.nn.conf.distribution.NormalDistribution
import org.deeplearning4j.nn.conf.layers.recurrent.LastTimeStep
import org.deeplearning4j.nn.conf.layers.{LSTM, OutputLayer}
import org.deeplearning4j.nn.conf.{MultiLayerConfiguration, NeuralNetConfiguration}
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork
import org.deeplearning4j.nn.weights.WeightInit
import org.deeplearning4j.util.ModelSerializer
import org.nd4j.linalg.activations.Activation
import org.nd4j.linalg.api.ndarray.INDArray
import org.nd4j.linalg.factory.Nd4j
import org.nd4j.linalg.lossfunctions.LossFunctions
import utils.{AppConfig, AppLogger}

import java.io.File
import java.util

/*
TransformerModel Model: Class for manage an embedding model.
It will start by reading a model param, building the model and then support get model output/weight
 */
object TransformerModel {
  private val logger = AppLogger("TransformerModel")
  def fromParam(param : ModelParam) : MultiLayerNetwork = {
    logger.debug("Config model from scratch")
    logger.info("Config with size Dim: " + param.embeddingDim)
    val config: MultiLayerConfiguration = new NeuralNetConfiguration.Builder()
      .list()
      .layer(new LastTimeStep(new LSTM.Builder()
        .nIn(param.embeddingDim)
        .nOut(256)
        .weightInit(new NormalDistribution(0, 1))
        .activation(Activation.TANH)
        .build()))
      .layer(new OutputLayer.Builder(LossFunctions.LossFunction.COSINE_PROXIMITY)
        .nIn(256)
        .nOut(param.embeddingDim)
        .weightInit(new NormalDistribution(0, 1))
        .activation(Activation.IDENTITY)
        .build())
      .build()

    new MultiLayerNetwork(config)
  }

  def fromPretrained(path : String) : MultiLayerNetwork = {
    val file = new File(path)
    ModelSerializer.restoreMultiLayerNetwork(file)
  }

  def testModel(model : MultiLayerNetwork) : Unit = {
    logger.info(model.summary())
    val rawInput: INDArray = Nd4j.create(Array(Array(3947.0, 548, 19171, 31153, 3947.0, 548, 19171, 31153, 3947.0, 23)))
    val layerInput = Nd4j.expandDims(rawInput.permute(1,0),0)
    model.getLayers.indices.map( i => {
      logger.info(model.getLayer(i).toString)
      logger.info(s"Layer $i input shape: ${util.Arrays.toString(layerInput.shape())}")
      val layerOutput = model.feedForwardToLayer(i, layerInput).get(i + 1)
      logger.info(s"Layer $i output shape: ${util.Arrays.toString(layerOutput.shape())}")
    })
  }

  def trainOneWord(model : MultiLayerNetwork, token: Int, predictToken: Int): Unit = {
    val inputFeatures : INDArray = Nd4j.create(Array(token.toDouble))
    val outputLabels : INDArray = Nd4j.create(Array(predictToken.toDouble))
    model.fit(inputFeatures, outputLabels)
  }

  def trainOneSample(model : MultiLayerNetwork, sentence : Array[Int]): Unit = {
    val inputFeatures : INDArray = Nd4j.create(Array(sentence.init))
    val outputLabels : INDArray = Nd4j.create(Array(sentence.head.toDouble))
    //    val numEpochs = 1; // Number of training epochs
    model.fit(inputFeatures, outputLabels)
  }

  def saveModel(model : MultiLayerNetwork, fileName: String) : Unit = model.save(new File(fileName))

  def main (args: Array[String]): Unit = {
    // Load the pretrained transformer model from file
    val modelPath = "path/to/your/pretrained_model.zip" // Path to the pretrained model file
    val model = TransformerModel.fromPretrained(modelPath)

    // Generate text using the pretrained model
    val query = "The cat"
//    String generatedSentence = generateSentence(query, model, 5);  // Generate a sentence with max 5 words
//    System.out.println("Generated Sentence: " + generatedSentence);
  }
}
