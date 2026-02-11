package snyk

import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import java.net.URLEncoder
import java.nio.file.FileSystem
import kotlin.properties.Delegates
import org.junit.rules.ExternalResource
import org.junit.runner.Description
import org.junit.runners.model.Statement

class InMemoryFsRule : ExternalResource() {
  private var _fs: FileSystem? = null
  private var sanitizedName: String by Delegates.notNull()

  override fun apply(base: Statement, description: Description): Statement {
    sanitizedName = URLEncoder.encode(description.methodName, Charsets.UTF_8.name())
    return super.apply(base, description)
  }

  val fs: FileSystem
    get() {
      if (_fs == null) {
        _fs = Jimfs.newFileSystem(Configuration.unix())
      }
      return _fs!!
    }

  override fun after() {
    _fs?.close()
    _fs = null
  }
}
