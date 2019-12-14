package com.tistory.deque.previewmaker.kotlin.model

import android.content.Context
import android.database.CursorIndexOutOfBoundsException
import android.graphics.Bitmap
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.annotation.RequiresApi
import com.tistory.deque.previewmaker.kotlin.util.EzLogger
import com.tistory.deque.previewmaker.kotlin.util.extension.getUri
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.ReplaySubject
import java.io.File
import java.io.FileNotFoundException

class PreviewLoader(private val context: Context) {

    companion object{
        const val THUMBNAIL_MAKE_METHOD_CALL_COUNT_MAX = 3
    }

    private val compositeDisposable = CompositeDisposable()

    val previewSubject: ReplaySubject<Preview> = ReplaySubject.create()

    private fun makePreviewSingle(previewPath: String): Single<Preview> {
        return Single.fromCallable {
            var thumbnailBitmap: Bitmap? = null
            var thumbnailUri: Uri? = null
            val originalUri: Uri = Uri.fromFile(File(previewPath))
            if (Build.VERSION.SDK_INT  >= Build.VERSION_CODES.Q) {
                thumbnailBitmap = getThumbnailBitmapFromOriginalUri(context, previewPath)
            } else {
                thumbnailUri = getThumbnailUriFromOriginalUri(context, previewPath) ?: originalUri
            }
            val rotation = try {
                ExifInterface(previewPath)
                        .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)
            } catch (e: FileNotFoundException) {
                ExifInterface.ORIENTATION_UNDEFINED
            }

            return@fromCallable Preview(originalUri, thumbnailUri, thumbnailBitmap, rotation)
        }
    }

    fun destroy() {
        compositeDisposable.clear()
    }

    fun startLoadPreview(previewPathList: ArrayList<String>) {
        previewPathList.forEach { previewPath ->
            compositeDisposable.add(makePreviewSingle(previewPath)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribeBy (
                            onSuccess = {
                                previewSubject.onNext(it)
                                EzLogger.d("Thumbnail parsing success : $it")
                            },
                            onError = {
                                EzLogger.d("Load preview error : $previewPath")
                            }
                    ))
        }
        previewSubject.onComplete()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun getThumbnailBitmapFromOriginalUri(context: Context, imagePath: String): Bitmap? {
        EzLogger.d("imagePath : $imagePath")
        val imageUri = imagePath.getUri(context.contentResolver) ?: return null
        EzLogger.d("original imageUri : $imageUri")
        val contentResolver = context.contentResolver
        return contentResolver.loadThumbnail(imageUri, Size(100, 100), null)
    }

    private fun getThumbnailUriFromOriginalUri(context: Context, imagePath: String): Uri? {
        EzLogger.d("imagePath : $imagePath")

        val selectedImageUri = imagePath.getUri(context.contentResolver) ?: return null
        val rowId = (selectedImageUri.lastPathSegment) ?: return null
        val rowIdLong: Long = rowId.toLongOrNull() ?: return null

        EzLogger.d("original uri : $selectedImageUri , row ID : $rowIdLong")

        return imageIdToThumbnail(context, rowIdLong, 0)
    }

    private fun imageIdToThumbnail(context: Context, imageId: Long, callCount: Int): Uri? {
        if (callCount >= THUMBNAIL_MAKE_METHOD_CALL_COUNT_MAX) return null

        val projection = arrayOf(MediaStore.Images.Thumbnails.DATA)
        val contentResolver = context.contentResolver

        try {
            contentResolver.query(
                    MediaStore.Images.Thumbnails.EXTERNAL_CONTENT_URI,
                    projection,
                    MediaStore.Images.Thumbnails.IMAGE_ID + "=?",
                    arrayOf(imageId.toString()),
                    null)
                    ?.use { thumbnailCursor ->
                        return if (thumbnailCursor.moveToFirst()) {
                            Uri.parse(thumbnailCursor.getString(thumbnailCursor.getColumnIndex(projection[0])))
                        } else {
                            MediaStore.Images.Thumbnails.getThumbnail(contentResolver, imageId, MediaStore.Images.Thumbnails.MINI_KIND, null)
                            //EzLogger.d("No exist thumbnail, so make it")
                            imageIdToThumbnail(context, imageId, callCount + 1)
                        }
                    } ?: return null
        } catch (e: CursorIndexOutOfBoundsException) {
            return null
        }
    }
}