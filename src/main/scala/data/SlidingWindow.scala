package data

import org.apache.spark.{SparkConf, SparkContext}
import utils.AppLogger

case class WindowData(input : List[String], target : String) {
}

object SlidingWindow {
  private val logger = AppLogger("EmbeddingModel")
  def createSlidingWindow(sentence : String, windowSize : Int) : List[WindowData] = {
    logger.info("Create sliding window" + sentence)
    sentence.split(" ").sliding(windowSize).collect {
      case window if (window.length == windowSize) => WindowData(window.init.toList, window.last)
    }.toList
  }

  def main (args: Array[String]): Unit = {
    val conf = new SparkConf().setAppName("Sliding Window Dataset").setMaster("local[*]")
    val sc = new SparkContext(conf)
    val sentences = List(
      "The quick brown fox jumps over the lazy dog",
      "This is another sentence for testing sliding windows")
    logger.info("Start parallelize")
    val distData = sc.parallelize(sentences)
    logger.info("Start parallelize")
    val slidingWindowDataset = distData.flatMap(sentence => createSlidingWindow(sentence, 4).iterator)

    slidingWindowDataset.collect().foreach(window => {
      logger.info("Input: " + window)
    })
    // Stop the Spark context
    sc.stop();
  }
}
