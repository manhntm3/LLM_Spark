package model

import org.deeplearning4j.nn.conf.layers.recurrent.LastTimeStep
import org.deeplearning4j.nn.conf.layers.{EmbeddingLayer, EmbeddingSequenceLayer, GlobalPoolingLayer, LSTM, OutputLayer, RnnOutputLayer}
import org.deeplearning4j.nn.conf.{MultiLayerConfiguration, NeuralNetConfiguration}
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork
import org.deeplearning4j.util.ModelSerializer
import org.nd4j.linalg.activations.Activation
import org.nd4j.linalg.api.buffer.DataType
import org.nd4j.linalg.api.ndarray.INDArray
import org.nd4j.linalg.factory.Nd4j
import org.nd4j.linalg.lossfunctions.LossFunctions
import utils.{AppConfig, AppLogger}

import java.io.File
import java.util

case class ModelParam(vocabSize: Int = 100000, embeddingDim: Int = 10)

object ModelParam {
  def apply(): ModelParam = {
    val p = AppConfig.getEmbeddingModel
    new ModelParam(
      p.getInt("vocabSize"),
      p.getInt("embeddingDim")
    )
  }
}

/*
TransformerModel Model: Class for manage an embedding model.
It will start by reading a model param, building the model and then support get model output/weight
 */
object EmbeddingModel {
  private val logger = AppLogger("EmbeddingModel")
  def fromParam(param : ModelParam) : MultiLayerNetwork = {
    logger.debug("Config model from scratch")
    logger.info("Config with size " + param.vocabSize + " Dim: " + param.embeddingDim)
    val config: MultiLayerConfiguration = new NeuralNetConfiguration.Builder()
      .list()
      .layer(new EmbeddingSequenceLayer.Builder()
        .nIn(param.vocabSize) // Input size (vocabulary size)
        .nOut(param.embeddingDim) // Embedding dimension
        .inputLength(4)
        .activation(Activation.IDENTITY) // No activation function
        .build())
      .layer(new LastTimeStep(new LSTM.Builder()
        .nIn(param.embeddingDim)
        .nOut(128)
        .activation(Activation.TANH)
        .build()))
      .layer(new OutputLayer.Builder(LossFunctions.LossFunction.SPARSE_MCXENT)
        .nIn(128)
        .nOut(param.vocabSize)
        .activation(Activation.SOFTMAX)
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
    val layerInput: INDArray = Nd4j.create(Array(Array(3947.0, 548, 19171, 31153)))
    for (i <- model.getLayers.indices) {
      println(model.getLayer(i).toString)
      println(s"Layer $i input shape: ${util.Arrays.toString(layerInput.shape())}")
      val layerOutput = model.feedForwardToLayer(i, layerInput).get(i + 1)
      println(s"Layer $i output shape: ${util.Arrays.toString(layerOutput.shape())}")
    }
  }

  def getParam(model : MultiLayerNetwork) : INDArray = model.getLayer(0).getParam("W")

  def getTokEmbedding(model : MultiLayerNetwork, token: Int) : List[Double] = getParam(model).getRow(token).toDoubleVector.toList

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

  def getCloseWord(model : MultiLayerNetwork, token: Int) : List[(Int, Double)] = {
    val embedding : INDArray = Nd4j.create(getTokEmbedding(model, token).toArray)
    findKClosestWords(getParam(model), embedding, 5)
  }

  def getCloseTok(model : MultiLayerNetwork, wordEmb: List[Double], numWords : Int): List[(Int, Double)] = {
    val embedding : INDArray = Nd4j.create(wordEmb.toArray)
    findKClosestWords(getParam(model), embedding, numWords)
  }

  def findKClosestWords(embeddings: INDArray, wordEmb: INDArray, k: Int): List[(Int, Double)] = {
    // Compute cosine similarity between word_emb and all embeddings

    logger.debug("Size of embeddings: " + embeddings.shape().mkString("Array(", ", ", ")"))
    logger.debug("Size of wordEmb: " + wordEmb.reshape(wordEmb.length(), 1).shape().mkString("Array(", ", ", ")"))

    val wordEmbColumn = if (wordEmb.rank() == 1 || (wordEmb.shape().length == 2 && wordEmb.shape()(0) == 1)) wordEmb.reshape(wordEmb.length(), 1) else wordEmb

    // Compute dot products between wordEmb and all embeddings
    logger.debug("Compute dot products  ")
    val dotProducts = embeddings.mmul(wordEmbColumn.castTo(DataType.FLOAT)) // Shape: (N, 1)

    logger.debug("Compute norms of embeddings: " + dotProducts.shape().mkString("Array(", ", ", ")"))
    // Compute norms of embeddings
    val embeddingsNorms = embeddings.norm2(1) // L2 norms along axis 1 (rows), shape: (N, 1)

    // Compute norm of wordEmb
    val wordEmbNorm = wordEmb.norm2Number().doubleValue()
    logger.debug("Compute cosine similarities: " + embeddingsNorms.shape().mkString("Array(", ", ", ")"))
    logger.debug("Compute cosine similarities: " + wordEmbNorm)
    val denominator = embeddingsNorms.mul(wordEmbNorm)
    val denominatorEmb = denominator.reshape(denominator.length(), 1)
    logger.debug("Compute cosine similarities: " + denominatorEmb.shape().mkString("Array(", ", ", ")"))
    // Compute cosine similarities
    val similarities = dotProducts.div(denominatorEmb) // Shape: (N, 1)
    logger.debug("similarities: " + similarities.shape().mkString("Array(", ", ", ")"))
    // Extract similarities as array
    val similaritiesArray = similarities.data().asDouble()

    // Create list of (index, similarity) pairs
    val similaritiesList = similaritiesArray.zipWithIndex.map { case (sim, idx) => (idx, sim) }.toList

    // Sort and get top k
    val topKSimilarities = similaritiesList.sortBy(-_._2).tail.take(k)

    topKSimilarities.foreach { case (index, score) =>
      logger.debug(s"Element $index. Score: $score")
    }

    topKSimilarities
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

