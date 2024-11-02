
import org.apache.spark.{SparkConf, SparkContext}


object sample {
  def main (args: Array[String]): Unit = {
    val logFile = "/opt/homebrew/Cellar/apache-spark/3.5.3/README.md" // Should be some file on your system
    val conf = new SparkConf().setAppName("app").setMaster("local[*]")
    val sc = new SparkContext(conf)
    val data = Array(1, 2, 3, 4, 5)
    val distData = sc.parallelize(data)
  }
}
