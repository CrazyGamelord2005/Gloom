package com.desolate.gloom.model

import kotlinx.serialization.Serializable

@Serializable
data class UserModel(
    var id : String = "",
    var email : String = "",
    var username : String = "",
    var profilePic : String = "",
    var followerList : MutableList<String> = mutableListOf(),
    var followingList : MutableList<String> = mutableListOf()
)
