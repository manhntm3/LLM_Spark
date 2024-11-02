package data
import org.apache.spark.{SparkConf, SparkContext}
import org.nd4j.linalg.api.ndarray.INDArray
import org.nd4j.linalg.dataset.DataSet
import utils.AppLogger
import model.{EmbeddingModel, ModelParam, Tokenizer}
import org.nd4j.linalg.factory.Nd4j

object SlidingWindowWithPositionalEmbedding extends Serializable {
  private val logger = AppLogger("SlidingWindowWithPositionalEmbedding")
  val tokenizer = new Tokenizer()
  val model = EmbeddingModel.fromPretrained("/Users/manh/ScalaProjects/Data/EMB.zip")

  def createSlidingWindowWithPositionalEmbedding(tokens : List[String], windowSize : Int) : List[DataSet] = {
    tokens.sliding(windowSize).collect {
      case window if (window.length == windowSize) => {
//        logger.debug("window: " + window)
        val input = Nd4j.expandDims(tokenizeAndEmbed(window.init).permute(1, 0), 0)
        val target = tokenizeAndEmbed(List(window.last))
//        logger.debug("input: " + input.shape().mkString("Array(", ", ", ")"))
//        logger.debug("target: " + target.shape().mkString("Array(", ", ", ")"))
        new DataSet(
          input,
          target)
      }
    }.toList
  }

  // Dummy method to simulate tokenization and embedding (replace with actual embedding code)
  def tokenizeAndEmbed(tokens : List[String]) : INDArray = {
    val listTokens : Array[Int] = tokens.map(w => tokenizer.tokenizeOne(w)).toArray
    val listEmbeddings : Array[Array[Double]] = listTokens.map( token => EmbeddingModel.getTokEmbedding(model, token).toArray)
    Nd4j.create(listEmbeddings)
  }

  def main (args: Array[String]): Unit = {
    val conf = new SparkConf().setAppName("Sliding Window Dataset with Position Embedding").setMaster("local[*]")
    val sc = new SparkContext(conf)
    val sentences = List(
      "The quick brown fox jumps over the lazy dog",
      "This is another sentence for testing sliding windows")
    logger.info("Start parallelize")
    val distData = sc.parallelize(sentences)
    logger.info("Start parallelize")
    val slidingWindowDataset = distData.flatMap(sentence => createSlidingWindowWithPositionalEmbedding(sentence.split(" ").toList, 4).iterator)

    slidingWindowDataset.collect().foreach(window => {
      logger.info("Input: " + window)
    })
    // Stop the Spark context
    sc.stop();
  }
}
