package com.desolate.gloom.model

import kotlinx.serialization.Serializable
import java.sql.Timestamp

@Serializable
data class VideoModel(
    var video_id : String = "",
    var title : String = "",
    var url : String = "",
    var uploader_id : String = "",
    var created_at : String = " "
)