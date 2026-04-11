package com.fyp.nextshot

import android.content.Context
import android.util.Base64
import android.widget.ImageView
import com.bumptech.glide.Glide

object ProfileUtils {
    fun loadProfileImage(context: Context, imageData: String?, imageView: ImageView, placeholder: Int) {
        if (imageData.isNullOrEmpty()) {
            imageView.setImageResource(placeholder)
            return
        }

        try {
            if (imageData.startsWith("data:image") || imageData.length > 200) {
                // Handle Base64
                val cleanData = if (imageData.startsWith("data:image")) {
                    imageData.substringAfter(",")
                } else {
                    imageData
                }
                val imageBytes = Base64.decode(cleanData, Base64.DEFAULT)
                Glide.with(context)
                    .load(imageBytes)
                    .circleCrop()
                    .placeholder(placeholder)
                    .error(placeholder)
                    .into(imageView)
            } else {
                // Handle regular URL
                Glide.with(context)
                    .load(imageData)
                    .circleCrop()
                    .placeholder(placeholder)
                    .error(placeholder)
                    .into(imageView)
            }
        } catch (e: Exception) {
            imageView.setImageResource(placeholder)
        }
    }
}