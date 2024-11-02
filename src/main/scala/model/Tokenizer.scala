package model

import com.knuddels.jtokkit.Encodings
import com.knuddels.jtokkit.api._

class Tokenizer {

  private val maximumToken = 100000
  private val registry: EncodingRegistry = Encodings.newDefaultEncodingRegistry()
  private val enc: Encoding = registry.getEncoding(EncodingType.CL100K_BASE)

  def tokenizeOne(word : String) : Int = {
    val result = enc.encode(word).get(0)
    if (result < maximumToken)
      result
    else {
      //just return the 0 if exceed maximum token
      0
    }
  }

}
