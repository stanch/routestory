package net.routestory.model

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URL
import org.apache.commons.io.IOUtils
import org.codehaus.jackson.annotate.JsonIgnore
import org.codehaus.jackson.annotate.JsonIgnoreProperties
import org.codehaus.jackson.annotate.JsonProperty
import org.ektorp.CouchDbConnector
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import org.ektorp.support.CouchDbDocument

@JsonIgnoreProperties(ignoreUnknown = true)
class Author extends CouchDbDocument with CouchDbObject {
  @JsonIgnore def bind(couch: CouchDbConnector) {}
  @JsonIgnore val pictureCache: Cache[Bitmap] = new Cache[Bitmap] {
    def getCacheFile(context: Context): File = {
      new File(s"${context.getExternalCacheDir.getAbsolutePath}/$getId-picture.bin")
    }
    def isCached(context: Context): Boolean = getCacheFile(context).exists
    def retrieve(context: Context): Bitmap = BitmapFactory.decodeFile(getCacheFile(context).getAbsolutePath)
    def cache(context: Context): Boolean = {
      if (picture == null) {
        false
      } else try {
        val cache = getCacheFile(context)
        val input = new URL(picture).getContent.asInstanceOf[InputStream]
        cache.setReadable(true, false)
        val output = new FileOutputStream(cache)
        IOUtils.copy(input, output)
        output.close()
        Log.v("Author", "Cached avatar for " + getId)
        true
      } catch {
        case e: Throwable ⇒ false
      }
    }
  }
  @JsonProperty("type") val `type`: String = "author"
  @JsonProperty("name") var name: String = _
  @JsonProperty("link") var link: String = _
  @JsonProperty("picture") var picture: String = _
}