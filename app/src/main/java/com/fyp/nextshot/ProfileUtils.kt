package com.fyp.nextshot

import android.content.Context
import android.util.Base64
import android.util.Log
import android.widget.ImageView
import com.bumptech.glide.Glide
import java.text.SimpleDateFormat
import java.util.*

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

    fun calculateAge(dobString: String?): String {
        if (dobString.isNullOrEmpty()) return "Age: N/A"
        
        return try {
            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val dob = sdf.parse(dobString) ?: return "Age: N/A"
            
            val dobCalendar = Calendar.getInstance()
            dobCalendar.time = dob
            
            val today = Calendar.getInstance()
            
            var age = today.get(Calendar.YEAR) - dobCalendar.get(Calendar.YEAR)
            
            if (today.get(Calendar.DAY_OF_YEAR) < dobCalendar.get(Calendar.DAY_OF_YEAR)) {
                age--
            }
            
            "Age: $age years"
        } catch (e: Exception) {
            "Age: N/A"
        }
    }
}