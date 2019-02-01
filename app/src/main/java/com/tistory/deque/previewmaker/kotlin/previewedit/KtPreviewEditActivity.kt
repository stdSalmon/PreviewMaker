package com.tistory.deque.previewmaker.kotlin.previewedit

import android.arch.lifecycle.Observer
import android.net.Uri
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.View
import com.tistory.deque.previewmaker.R
import com.tistory.deque.previewmaker.kotlin.base.BaseKotlinActivity
import com.tistory.deque.previewmaker.kotlin.util.EtcConstant
import com.tistory.deque.previewmaker.kotlin.util.EzLogger
import kotlinx.android.synthetic.main.activity_kt_preview_edit.*
import org.koin.android.ext.android.inject
import org.koin.android.viewmodel.ext.android.viewModel

class KtPreviewEditActivity : BaseKotlinActivity<KtPreviewEditViewModel>() {

    override val layoutResourceId: Int
        get() = R.layout.activity_kt_preview_edit
    override val viewModel: KtPreviewEditViewModel by viewModel()

    var stampId: Int = -1
    var stampImageUri: Uri? = null
    var previewPathList: ArrayList<String> = ArrayList()

    private val previewThumbnailAdapter: PreviewThumbnailAdapter by inject()

    override fun initViewStart() {
        setBackButtonAboveActionBar(true, "프리뷰 편집")
        setPreviewThumbnailRecyclerView()
        addComponentFromIntent()
    }

    private fun addComponentFromIntent() {
        intent.run {
            stampId = getIntExtra(EtcConstant.STAMP_ID_INTENT_KEY, -1)
            stampImageUri = data
            previewPathList = getStringArrayListExtra(EtcConstant.PREVIEW_LIST_INTENT_KEY)
        }
    }

    private fun setPreviewThumbnailRecyclerView() {
        preview_edit_thumbnail_recycler_view.run {
            adapter = previewThumbnailAdapter.apply {
                previewListModel = viewModel.previewListModel
                previewThumbnailClickListener = { preview ->
                    viewModel.previewThumbnailClickListener(applicationContext, preview)
                }
            }
            layoutManager = LinearLayoutManager(applicationContext, RecyclerView.HORIZONTAL, false)
            setHasFixedSize(true)
        }
    }

    override fun initDataBinding() {
        viewModel.startLoadingThumbnailEvent.observe(this, Observer { size ->
            preview_edit_thumbnail_loading_progress_bar.run {
                post {
                    visibility = View.VISIBLE
                    max = size ?: EtcConstant.MAX_SELECT_IMAGE_ACCOUNT
                }
            }
        })
        viewModel.loadingFinishEachThumbnailEvent.observe(this, Observer { position ->
            position?.let {
                EzLogger.d("loadingFinishEachThumbnailEvent observe position : $position")
                previewThumbnailAdapter.notifyDataSetChanged()
                preview_edit_thumbnail_loading_progress_bar.run { post { progress = position } }
            }
        })
        viewModel.finishLoadingThumbnailEvent.observe(this, Observer { size ->
            previewThumbnailAdapter.notifyDataSetChanged()
            preview_edit_thumbnail_loading_progress_bar.run { post { visibility = View.GONE } }
        })
        viewModel.startLoadingPreviewToCanvas.observe(this, Observer {
            preview_edit_hint_text_view.run { post { visibility = View.GONE } }
            mainLoadingProgressBarStart()
        })
        viewModel.stopLoadingPreviewToCanvas.observe(this, Observer {
            mainLoadingProgressBarStop()
        })
    }

    override fun initViewFinal() {
        viewModel.makePreviewThumbnail(applicationContext, previewPathList)
    }

    private fun mainLoadingProgressBarStart(){
        preview_edit_loading_progress_bar_layout.run { post { visibility = View.VISIBLE } }
    }

    private fun mainLoadingProgressBarStop(){
        preview_edit_loading_progress_bar_layout.run { post { visibility = View.GONE } }
    }
}
