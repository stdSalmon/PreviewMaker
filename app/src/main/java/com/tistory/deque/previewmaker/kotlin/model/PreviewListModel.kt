package com.tistory.deque.previewmaker.kotlin.model

class PreviewListModel(){

    val previewArrayList: ArrayList<Preview> = ArrayList()

    val size : Int
        get() = previewArrayList.size

    fun getPreview(position: Int): Preview = previewArrayList[position]
    fun addPreview(preview: Preview) {
        previewArrayList.add(preview)
    }

}