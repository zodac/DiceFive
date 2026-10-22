package net.zodac.dicefive

import android.content.Context
import org.xmlpull.v1.XmlPullParser

/**
 * The resource the packaged manifest's <application> names in android:[name], or 0 for none - read from the manifest
 * itself, for attributes Robolectric doesn't fill into ApplicationInfo (android:dataExtractionRules, fullBackupContent).
 */
internal fun applicationManifestResource(context: Context, name: String): Int {
    val parser = context.assets.openXmlResourceParser("AndroidManifest.xml")
    while (parser.next() != XmlPullParser.END_DOCUMENT) {
        if (parser.eventType == XmlPullParser.START_TAG && parser.name == "application") {
            return parser.getAttributeResourceValue("http://schemas.android.com/apk/res/android", name, 0)
        }
    }
    return 0
}
